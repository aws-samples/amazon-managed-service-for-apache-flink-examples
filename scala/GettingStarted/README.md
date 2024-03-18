## Getting Started Flink Scala project - DataStream API

Skeleton project for a basic Flink Java application to run on Amazon Managed Service for Apache Flink.

* Flink version: 1.18
* Scala version: 3.3.0
* Flink API: DataStream API
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application shows how to get runtime configuration, and sets up a Kinesis Data Stream source and a sink.

### Pre-requisites
You need to have `sbt` tool installed on you machine to build a Scala project. Use [steps from official guide](https://www.scala-sbt.org/download.html) to do that.

### Build
- Run `sbt assembly` to build an uber jar
- Use `target/scala-3.3.0/getting-started-scala-1.0.jar` in your MSF application

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group IDs `ConsumerGroupProperties` and `ProducerConfigProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:
* **ConsumerConfigProperties**:  
  * `input.stream.name` : name of the input Kinesis Data Stream (default: ExampleInputStream)
  * `aws.region`: region of the input stream (default: us-east-1)
* **ProducerConfigProperties**:
  * `output.stream.name` : name of the output Kinesis Data Stream (default: ExampleOutputStream)
  * `aws.region`: region of the output stream (default: us-east-1)

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator),
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.
