# Automatic Scaling for Amazon Managed Service for Apache Flink Applications

* Python 3.9 for Lambda Function


**IMPORTANT:** We strongly recommend that you disable autoscaling within your Amazon Managed Service for Apache Flink application if using the approach described here.

This sample illustrates how to scale your Amazon Managed Service for Apache Flink application using a different CloudWatch Metric from the Apache Flink Application, Amazon MSK, Amazon Kinesis Data Stream. Here's the high level approach:

- Using Amazon CloudWatch Alarms for the select metric in order to trigger Scaling Step Function Logic
- Step Functions that triggers the AWS Lambda Scaling Function, which verifies if the alarm after metric has gone into OK status
- AWS Lambda Function to Scale Up/Down the Managed Flink Application, as well as verifying the Application doesn't go above or below maximum/minimum KPU.

## Deploying the AutoScaling for Managed Flink Applications

Follow the instructions to deploy the autoscaling solution in your AWS Account

1. Clone this repository
2. Go to CloudFormation
3. Click Create stack
4. Select `Upload a template file`
5. Upload the template from this repository
6. This deployment takes the following CFN Parameters
   1. **Amazon Managed Service for Apache Flink AutoScaling Configuration:**

      1. *Amazon Managed Service for Apache Flink Application Name*: The name of the Amazon Managed Apache Flink Application you would want to Auto Scale
      2. *Auto Scale Metric*: Available metrics to use for Autoscaling.
      3. *Custom Metric Name*: If you choose custom metric to do scaling, please provide its name. Remember that it will only work if you add as dimension to the Metric group **kinesisAnalytics**
      3. *Maximum KPU*: Maximum number of KPUs you want the Managed Flink Application to Scale
      4. *Minimum KPU*: Minimum number of KPUs you want the Managed Flink Application to Scale
   2. [**CloudWatch Alarm Configuration:**](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html#alarm-evaluation)

      5. *Evaluation Period for Metric*: Period to be used in minutes for the evaluation for scaling in or out (Example: 5)
      6. *Number of Data points to Trigger Alarm*: Number of Data Points (60 seconds each data point) during Evaluation Period where Metric has to be over threshold for rule to be in Alarm State. (Example: 2 This would mean that alarm would trigger if during a 5 minute window, 2 data points are above threshold)
      7. Waiting time for Application to finish updating (Seconds)
      7. *Grace Period for Alarm*: Time given in seconds to application after scaling to have alarms go to OK status (Example: 120)
   3. **Scale In/Out Configuration:**

      8. *Scale Out/In Operation*: Scale Out/In Operation (Multiply/Divide or Add/Substract)
      9. *Scale In Factor*: Factor by which you want to reduce the number of KPUs in your Flink Application
      10. *Threshold for Metric to Scale Down*: Choose the threshold for when the Scale In Rule should be in Alarm State
      11. *Scale Out Factor*: Factor by which you want to increase the number of KPUs in your Flink Application
      12. *Threshold for Metric to Scale Up*: Choose the threshold for when the Scale Out Rule should be in Alarm State
   4. **Kafka Configuration:**

      13. *Amazon MSK Cluster Name*: If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the name of the MSK Cluster for monitoring
      14. *Kafka Topic Name*: If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the Kafka Topic for monitoring
      15. *Kafka Consumer Group*: If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the Consumer Group name for monitoring
   5. **Kinesis Configuration:**

      16. *Kinesis Data Streams Name*: If you choose MillisBehindLatest as metric to scale, you need to provide the Kinesis Data Stream Name for monitoring


## Scaling logic

As alluded to above, the scaling logic is a bit more involved than simply calling `UpdateApplication` on your Amazon Managed Service for Apache Flink application. Here are the steps involved:

The Solution will trigger an alarm based on the threshold set in the parameter of the CloudFormation Template for the selected metric. This will activate an AWS Step Functions Workflow which will

* Scale the Managed Flink Application In or Out using an increase factor defined in the CloudFormation Template.
* Once the application has finished updating, it will verify if it has reached the minimum or maximum value for KPUs that the application can have. If it has it will finish the scaling event.
* If the application hasn't reached the max/min values of allocated KPU’s, the workflow will wait for a given period of time, to allow the metric to fall within threshold and have the Amazon CloudWatch Rule from ALARM status to OK.
* If the rule is still in ALARM status, the workflow will scale again the application. If the rule is now in OK, it will finish the scaling event.



NOTE: In this sample, we assume that the parallelism/KPU is 1. For more background on parallelism and parallelism/KPU, please see [Application Scaling in Amazon Managed Service for Apache Flink](https://docs.aws.amazon.com/kinesisanalytics/latest/java/how-scaling.html).

## References

- [Amazon Managed Service for Apache Flink developer guide](https://docs.aws.amazon.com/kinesisanalytics/latest/java/what-is.html).
- [Application Scaling in Amazon Managed Service for Apache Flink](https://docs.aws.amazon.com/kinesisanalytics/latest/java/how-scaling.html).
- [KinesisAnalyticsV2 boto3 reference](https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/kinesisanalyticsv2.html).