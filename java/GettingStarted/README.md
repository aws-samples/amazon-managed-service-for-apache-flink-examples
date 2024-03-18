## Getting Started Flink Java project - DataStream API

Skeleton project for a basic Flink Java application to run on Amazon Managed Service for Apache Flink.

* Flink version: 1.18
* Flink API: DataStream API
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application shows how to get runtime configuration, and sets up a Kinesis Data Stream source and a sink.

### Runtime configuration

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

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to 
the classpath'*.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator), 
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.
