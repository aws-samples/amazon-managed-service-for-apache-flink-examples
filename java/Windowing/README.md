## Getting Started Flink Java project - Windowing

Example of project for a basic Flink Java application using Tumbling and Sliding windows.

* Flink version: 1.15.2
* Flink API: DataStream API
* Flink Connectors: Kafka Connector, Kinesis Connector
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application reads from a Kafka source topic and writes to Kafka destination topic, showing how to implement a simple events count using tumbling window assigner. 

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

`TumblingWindowStreamingJob`:
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

`SlidingWindowStreamingJob`:
* `InputStreamRegion` region of the input stream (default: `us-east-1`)
* `InputStreamName` name of the input Kinesis Data Stream (default: `ExampleInputStream`)
* `OutputStreamRegion` region of the input stream (default: `us-east-1`)
* `OutputStreamName` name of the input Kinesis Data Stream (default: `ExampleOutputStream`)

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.
Provide arguments like following for Kafka windowing samples-
```
--brokers localhost:9092 --kafka-source-topic source --kafka-sink-topic sink --transaction.timeout.ms 1000
```
For Kinesis sample -
```
--InputStreamRegion us-east-1 --InputStreamName source-stream --OutputStreamRegion us-east-1 --OutputStreamName sink-stream
```

### Running locally through MVN command line
Refer following samples -
```
 mvn exec:java  -Dexec.classpathScope="compile" -Dexec.mainClass="com.amazonaws.services.msf.windowing.SlidingWindowStreamingJobKafkaWithParallelism" -Dexec.args="--brokers localhost:9092 --kafka-source-topic source --kafka-sink-topic sink --transaction.timeout.ms 1000" 

```
```
 mvn exec:java  -Dexec.classpathScope="compile" -Dexec.mainClass="com.amazonaws.services.msf.windowing.TumblingWindowStreamingJob" -Dexec.args="--brokers localhost:9092 --kafka-source-topic source --kafka-sink-topic sink --transaction.timeout.ms 1000" 

```
### Deploying using CloudFormation to Amazon Managed Service for Apache Flink
Refer the sample CloudFormation template at `cloudformation/windowing-msk-serverless-cloudformation.yaml` .
The sample assumes that both source and sink topics are on same cluster and clients need IAM auth.
Edit `deploy-windowing-msk.sh` and execute following command. The command optionally builds jar and uploads to provided bucket.

#### Tumbling window MSK Serverless 
```
./deploy-windowing-msk.sh TUMBLING <bucket-name-to-upload> <Y/N If build code and upload or not>
```
To build code and deploy run -  
```
./deploy-windowing-msk.sh TUMBLING <bucket-name-to-upload> 
```
This will build the jar with main class `com.amazonaws.services.msf.windowing.TumblingWindowStreamingJob` and upload to  `s3://<bucket-name-to-upload>/flink/tumbling-window-kafka-amazon-app-1.0.jar`

If you are just changing CloudFormation parameters or jobs parameters , then the build may not be required. In that case run - 
```
./deploy-windowing-msk.sh TUMBLING <bucket-name-to-upload>  N
```
#### Sliding window MSK Serverless
```
./deploy-windowing-msk.sh SLIDING <bucket-name-to-upload> <Y/N If build code and upload or not>
```
To build code and deploy run -
```
./deploy-windowing-msk.sh  SLIDING <bucket-name-to-upload> 
```
This will build the jar with main class `com.amazonaws.services.msf.windowing.SlidingWindowStreamingJobKafkaWithParallelism` and upload to  `s3://<bucket-name-to-upload>/flink/sliding-window-kafka-amazon-app-1.0.jar`

If you are just changing CloudFormation parameters or jobs parameters , then the build may not be required. In that case run -
```
./deploy-windowing-msk.sh SLIDING <bucket-name-to-upload>  N
```
### Data generator - Kafka
The project includes a [simple Python script](./data-generator/generator.py) that generates data and publishes
to Kafka. 
Edit the script to change the boostrap brokers and topic name.

### Data generator - Kinesis
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

