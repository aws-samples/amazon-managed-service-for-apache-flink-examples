# Getting Started Flink Java project - Windowing

Example of project for a basic Flink Java application using Tumbling and Sliding windows.

* Flink version: 1.15.2
* Flink API: DataStream API
* Flink Connectors: Kafka Connector, Kinesis Connector
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.
There are two sample applications which show windowing example. 

### Kafka - Tumbling window*
`com.amazonaws.services.msf.windowing.kafka.TumblingWindowStreamingJob`

![Flink Example](flink-kafka-example.png),

The application reads from a Kafka source topic and writes to Kafka destination topic, 
showing how to implement a simple events count using tumbling window assigner.

### Kinesis - Sliding window*
`com.amazonaws.services.msf.windowing.kinesis.SlidingWindowStreamingJobWithParallelism`

![Flink Example](flink-kinesis-example.png),

The application reads from a Kinesis source stream and writes to Kinesis destination stream,
showing how to implement a simple minimum price calculation for each stock using sliding window assigner.


## Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

### Kafka - Tumbling window* `kafka.TumblingWindowStreamingJob`:
* `kafka-source-topic` source topic
* `kafka-sink-topic` source topic
* `brokers` source Kafka cluster boostrap servers 

Additional parameters for IAM auth which is required for MSK Serverless
* `sasl.mechanism=AWS_MSK_IAM`
* `sasl.client.callback.handler.class=software.amazon.msk.auth.iam.IAMClientCallbackHandler`
* `sasl.jaas.config=software.amazon.msk.auth.iam.IAMLoginModule required;"`
* `security.protocol=SASL_SSL`
* `ssl.truststore.location=/usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts`
* `ssl.truststore.password=changeit`

### Kinesis - Sliding window* `kinesis.SlidingWindowStreamingJobWithParallelism`:
* `InputStreamRegion` region of the input stream (default: `us-east-1`)
* `InputStreamName` name of the input Kinesis Data Stream (default: `ExampleInputStream`)
* `OutputStreamRegion` region of the input stream (default: `us-east-1`)
* `OutputStreamName` name of the input Kinesis Data Stream (default: `ExampleOutputStream`)

## Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.
### Kafka - Tumbling window* `kafka.TumblingWindowStreamingJob` :
```
--brokers localhost:9092 --kafka-source-topic windowing-source --kafka-sink-topic windowing-tumbling-sink
```

### Kinesis - Sliding window* `kinesis.SlidingWindowStreamingJobWithParallelism` :
```
--InputStreamRegion ap-south-1 --InputStreamName stream-input --OutputStreamRegion ap-south-1 --OutputStreamName stream-windowing-sliding-output
```

## Running locally through MVN command line
### Kafka - Tumbling window* `kafka.TumblingWindowStreamingJob` :
```
 mvn exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.windowing.kafka.TumblingWindowStreamingJob" \
 -Dexec.args="--brokers localhost:9092 --kafka-source-topic windowing-source --kafka-sink-topic windowing-tumbling-sink" 

```

### Kinesis - Sliding window* `kinesis.SlidingWindowStreamingJobWithParallelism` :
```
 mvn exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.windowing.kinesis.SlidingWindowStreamingJobWithParallelism" \
 -Dexec.args="--InputStreamRegion ap-south-1 --InputStreamName stream-input --OutputStreamRegion ap-south-1 --OutputStreamName stream-windowing-sliding-output" 

```
## Deploying using CloudFormation to Amazon Managed Service for Apache Flink

### Kafka - Tumbling window `kafka.TumblingWindowStreamingJob` 

#### Pre-requisite
1. Create MSK serverless cluster while choosing 3 subnets. Refer https://docs.aws.amazon.com/msk/latest/developerguide/serverless-getting-started.html .
2. Once the cluster is created note down subnets ids of the cluster and security group.
3. Ensure that security group has self referencing ingress rule that allows connection on port 9098.

#### Build and deployment

1. Build Code. Execute the script below which will build the jar and upload the jar to S3 at s3://<bucket-name>/flink/kafka-windowing-tumbling-1.0.jar.
```shell
./build-kafka-sample.sh <bucket-name-to-upload>
```
2. Edit `deploy-kafka-sample.sh` to modify  "Region and Network configuration" . Modify following configurations -
* region= Deployment region
* SecurityGroup= MSK Security Group.
* SubnetOne= MSK Subnet one
* SubnetTwo= MSK Subnet two
* SubnetThree= MSK Subnet three
3. Edit `deploy-kafka-sample.sh` to modify "MSK configuration". Modify following configurations -
* kafka_bootstrap_server= MSK Serverless bootstrap server.
* source_topic= Source topic.
* sink_topic= Sink topic.

  Ensure that source and sink topics are created.
4. Run `deploy-kafka-sample.sh` to deploy the CloudFormation template . Refer the sample CloudFormation template at `cloudformation/msf-msk-iam-auth-windowing.yaml` .
   The CloudFormation needs the jar to be there at s3://<bucket-name>/flink/kafka-windowing-tumbling-1.0.jar.

```
./deploy-kafka-sample.sh <bucket-name-to-upload> 
```
5. The template creates following resources -
* Flink application with application name defined by application_name in deploy.sh.
* CloudWatch log group with name - /aws/amazon-msf/${application_name}
* CloudWatch log stream under the log group created above by name amazon-msf-log-stream.
* IAM execution role for Flink application. The role permission on MSK cluster.
* IAM managed policy.

### Kinesis - Sliding window `kinesis.SlidingWindowStreamingJobWithParallelism`
#### Pre-requisite
1. Source and sink stream. 
2. Create subnets and security groups for the Flink application. If you are using private subnets , ensure that VPC endpoint for Kinesis is created. 

#### Build and deployment

1. Build Code. Execute the script below which will build the jar and upload the jar to S3 at s3://<bucket-name>/flink/kinesis-windowing-sliding-1.0.jar.
```shell
./build-kinesis-sample.sh <bucket-name-to-upload>
```
2. Edit `deploy-kinesis-sample.sh` to modify  "Region and Network configuration" . Modify following configurations -
* region= Deployment region
* SecurityGroup= MSK Security Group.
* SubnetOne= MSK Subnet one
* SubnetTwo= MSK Subnet two
* SubnetThree= MSK Subnet three
3. Edit `deploy-kinesis-sample.sh` to modify "Kinesis configuration". Modify following configurations -
* input_stream= Input Kinesis stream name.
* output_stream= Output stream name

  Ensure that source and sink streams are created.
4. Run `deploy-kafka-sample.sh` to deploy the CloudFormation template . Refer the sample CloudFormation template at `cloudformation/msf-kinesis-stream-windowing.yaml` .
   The CloudFormation needs the jar to be there at s3://<bucket-name>/flink/kinesis-windowing-sliding-1.0.jar.

```
./deploy-kafka-sample.sh <bucket-name-to-upload> 
```
5. The template creates following resources -
* Flink application with application name defined by application_name in deploy.sh.
* CloudWatch log group with name - /aws/amazon-msf/${application_name}
* CloudWatch log stream under the log group created above by name amazon-msf-log-stream.
* IAM execution role for Flink application. The role permission on MSK cluster.
* IAM managed policy.
## Data generator - Kafka
The project includes a [simple Python script](./data-generator/generator.py) that generates data and publishes
to Kafka. 
Edit the script to change the boostrap brokers and topic name.

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

