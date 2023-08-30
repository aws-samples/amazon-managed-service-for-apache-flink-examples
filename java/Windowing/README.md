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

`SlidingWindowStreamingJob`:
* `InputStreamRegion` region of the input stream (default: `us-east-1`)
* `InputStreamName` name of the input Kinesis Data Stream (default: `ExampleInputStream`)
* `OutputStreamRegion` region of the input stream (default: `us-east-1`)
* `OutputStreamName` name of the input Kinesis Data Stream (default: `ExampleOutputStream`)

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

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
