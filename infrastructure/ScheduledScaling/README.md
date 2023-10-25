# Scheduled Scaling of Amazon Managed Service for Apache Flink app w/ Amazon EventBridge and AWS Lambda

* Python 3.9 for Lambda Function


**IMPORTANT:** We strongly recommend that you disable autoscaling within your Amazon Managed Service for Apache Flink application if using the approach described here.

This sample illustrates how to scale your Amazon Managed Service for Apache Flink application on a schedule using Amazon EventBridge and AWS Lambda. Here's the high level approach:

- Use Amazon EventBridge to call an AWS Lambda function on a schedule.
- In the AWS Lambda function, call `UpdateApplication` on your Amazon Managed Service for Apache Flink application.

Of course, there's a bit more to it than the above 2 steps. For instance, we have to do proper error handling and ensure that we're not updating an application that is already at the expected parallelism. These details and more are explained in the sections following the section below.

## Deploying the schedule based scaler

Follow the instructions to deploy the scheduled based scaler in your AWS Account

1. Clone this repository 
2. Go to CloudFormation
3. Click Create stack
4. Select `Upload a template file`
5. Upload the template from this repository
6. This deployment takes the following CFN Parameters 
   1. *msfAppName*: The Amazon Managed Service for Apache Flink Application that you want to scheduled scaling
   2. *lowKPU*: The number of KPU during off peak
   3. *highKPU*: The number of KPU during peak
   4. *ScaleUpHour*: Time of the day to scale up the application
   5. *ScaleDownHour*: Time of the day to scale down the application

*Important: The scale up Hour and Scale Down Hour are in UTC*

## Scaling logic

As alluded to above, the scaling logic is a bit more involved than simply calling `UpdateApplication` on your Amazon Managed Service for Apache Flink application. Here are the steps involved:

1. Call `DescribeApplication` to get the current version id and application status. The current application version id needs to be supplied as a parameter when calling `UpdateApplication` - for optimistic concurrency reasons.
2. If application status is not RUNNING, then we simply exit.
3. We then check to see if we're currently in a high scale period or a low scale period.
4. Before updating the application's KPU count, we also check to see if the current KPU count is already at the expected value. If so, we simply exit.
5. If we've gotten this far, it means that we need to call `UpdateApplication` with the desired KPU count as well as the current application version.

NOTE: In this sample, we assume that the parallelism/KPU is 1. For more background on parallelism and parallelism/KPU, please see [Application Scaling in Amazon Managed Service for Apache Flink](https://docs.aws.amazon.com/kinesisanalytics/latest/java/how-scaling.html).

## Error handling

The included Python code catches exceptions and prints an error message. We recommend that you handle errors in a more fine grained fashion based on the guidance here: [error handling in Boto3](https://boto3.amazonaws.com/v1/documentation/api/latest/guide/error-handling.html).

## Parametrizing application properties

You might consider parametrizing key application variables in your AWS Lambda function such as the region and the name of the application being scaled using AWS Lambda environment variables. Please see [Using AWS Lambda environment variables](https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html) for more details.

## Pricing

Please keep in mind that there are costs associated with the key components used in this sample:

- [Amazon EventBridge pricing](https://aws.amazon.com/eventbridge/pricing/).
- [Amazon Lambda pricing](https://aws.amazon.com/lambda/pricing/).

## Troubleshooting

1. Access denied exception of the form: `An error occurred (AccessDeniedException) when calling the DescribeApplication operation`.

   Please ensure that you've given the role associated with your AWS Lambda function permissions to call `DescribeApplication` and `UpdateApplication` on your Amazon Managed Service for Apache Flink application.

2. Amazon Managed Service for Apache Flinks application is not scaled unless it is running.

   This is by design. The included Python code first checks to see if the Amazon Managed Service for Apache Flink application is running and only updates the KPU count if the application has a status of RUNNING.

## References

- [Amazon Managed Service for Apache Flink developer guide](https://docs.aws.amazon.com/kinesisanalytics/latest/java/what-is.html).
- [Application Scaling in Amazon Managed Service for Apache Flink](https://docs.aws.amazon.com/kinesisanalytics/latest/java/how-scaling.html).
- [KinesisAnalyticsV2 boto3 reference](https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/kinesisanalyticsv2.html).
- [Amazon EventBridge developer guide](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-what-is.html).
- [Boto3 error handling](https://boto3.amazonaws.com/v1/documentation/api/latest/guide/error-handling.html).