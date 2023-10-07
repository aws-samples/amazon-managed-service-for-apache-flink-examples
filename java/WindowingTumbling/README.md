# Getting Started Flink Java project - Windowing

Example of project for a basic Flink Java application using Tumbling and Sliding windows.

* Flink version: 1.15
* Flink API: DataStream API
* Flink Connectors: Kafka Connector, Kinesis Connector
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.
There is one sample application which shows tumbling windowing example. 

### Kinesis - Tumbling window*
`com.amazonaws.services.msf.windowing.kinesis.TumblingWindowStreamingJob`

![Flink Example](images/flink-kinesis-example.png)

The application reads from a Kinesis source stream and writes to Kinesis destination stream,
showing how to implement a simple count calculation for each stock ticks using tumbling window assigner.

### Sample Input
```json
{"price": 10.0, "ticker":"AMZN"}
```
### Sample Output
```
("AMZN",58)
```

## Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `InputStreamRegion` region of the input stream (default: `us-east-1`)
* `InputStreamName` name of the input Kinesis Data Stream (default: `ExampleInputStream`)
* `OutputStreamRegion` region of the input stream (default: `us-east-1`)
* `OutputStreamName` name of the input Kinesis Data Stream (default: `ExampleOutputStream`)

## Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

```
--InputStreamRegion ap-south-1 --InputStreamName stream-input --OutputStreamRegion ap-south-1 --OutputStreamName stream-windowing-output
```

Following is the screenshot of run configuration
![Run Configuration](images/runConfiguration.png)
## Running locally through Maven command line

```
 mvn clean compile exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.windowing.kinesis.TumblingWindowStreamingJob" \
 -Dexec.args="--InputStreamRegion ap-south-1 --InputStreamName stream-input --OutputStreamRegion ap-south-1 --OutputStreamName stream-windowing-tumbling-output" 

```
## Deploying using CloudFormation to Amazon Managed Service for Apache Flink
### Pre-requisite
1. Source and sink stream. 
2. Create subnets and security groups for the Flink application. If you are using private subnets , ensure that VPC endpoint for Kinesis is created. 
3. AWS user credential using which you can create CloudFormation stack from console or CLI.

### Build and deployment
1. The steps below create stack using `./cloudformation/msf-kinesis-stream-windowing.yaml`.
2. The script `deploy.sh` creates the stack using AWS CLI. Ensure that AWS CLI is configured and your user has permissions to create CloudFormation stack.
3. Alternatively you can deploy from console using `./cloudformation/msf-kinesis-stream-windowing.yaml` and pass required parameters.
4. Edit `deploy.sh` to modify  "Region and Network configuration" . Modify following configurations -
* region= Deployment region
* SecurityGroup= MSK Security Group.
* SubnetOne= MSK Subnet one
* SubnetTwo= MSK Subnet two
* SubnetThree= MSK Subnet three

5. Edit `deploy.sh` to modify "Kinesis configuration". Modify following configurations -
* input_stream= Input Kinesis stream name.
* output_stream= Output stream name
  Ensure that source and sink streams are created.

6. Build Code. Execute the script below which will build the jar and upload the jar to S3 at s3://BUCKET_NAME/flink/amazon-msf-windowing-tumbling-app-1.0.jar.
```shell
./build.sh <BUCKET_NAME>
```
7. Run `deploy.sh` to deploy the CloudFormation template . Refer the sample CloudFormation template at `cloudformation/msf-kinesis-stream-windowing.yaml` .
   The CloudFormation needs the jar to be there at s3://BUCKET_NAME/flink/amazon-msf-windowing-tumbling-app-1.0.jar.

```
./deploy.sh <BUCKET_NAME> 
```
8. The template creates following resources -
* Flink application with application name defined by application_name in deploy.sh.
* CloudWatch log group with name - /aws/amazon-msf/${application_name}
* CloudWatch log stream under the log group created above by name amazon-msf-log-stream.
* IAM execution role for Flink application. The role permission on MSK cluster.
* IAM managed policy.


## Data generator - Kinesis
You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator),
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.

RecordTemplate:

`{"price": {{random.number.float({
"min":1,
"max":99,
"precision": 0.01
})}}, "ticker":"{{random.arrayElement(
["AAPL","AMZN","MSFT","INTC","TBV"]
)}}"}`

