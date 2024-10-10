## Sample illustrating how to use Async I/O for Apache Flink with Retries
* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Connectors: [Datagen](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/datagen/), [Kinesis](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/kinesis/)

This sample illustrates how to leverage the Async I/O pattern in Apache Flink, with retries on errors and timeouts. More details on Apache Flink's Async I/O Functionality can be found [here](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/asyncio/)

Data is generated from a Datagen Source connector. The data is then run through an AsyncWaitOperator, which calls an API Gateway endpoint to return a response. The infrastructure for the API Endpoint can be launched via CloudFormation template, with instructions on how to do so below.

The application generates data internally and writes to a Kinesis Stream.

### Set up Cloud Infrastructure
In order to run this example, you will need to have an endpoint to query against. We have provided an [AWS CloudFormation Template](src/main/resources/lambda-cloudformation.yaml) to launch the following resources in your AWS Account:
- **IAM Role (LambdaExecutionRole)**: Creates a role for Lambda to write logs to CloudWatch.
- **Lambda Function (RandomResponseLambda)**: Deploys a Python Lambda function that randomly returns different HTTP statuses.
- **API Gateway (ApiGateway)**: Exposes the Lambda function via an HTTP endpoint.

**Total Estimated Cost**: 
Approximately $3.70 per million requests (combined Lambda and API Gateway costs after the free tier).


### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.


Runtime parameters:

Here is the JSON data in the desired table format, with personally identifiable information (PII) removed and replaced with `X's`:

| Group ID          | Key           | Description                                                                                                                                                  |
|-------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OutputStream0`    | `stream.arn` | ARN of the output stream.                                                                                                                                   |
| `OutputStream0`    | `aws.region`  | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |
| `EndpointService` | `api.url`     | API URL for accessing the API Gateway Endpoint (found in CFN Outputs Tab)                                                                                    |
| `EndpointService` | `api.key`     | API key for authentication to the API Gateway Endpoint (found in CFN Outputs Tab)                                                                            |
| `EndpointService` | `aws.region`  | (Optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. | 

All parameters are case-sensitive.

This simple example assumes the Kinesis Stream is in the same region as the application, or in the default region for the authentication profile, when running locally.


### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.