## Getting Started Flink Scala project - DataStream API

Skeleton project for a basic Flink Java application to run on Amazon Managed Service for Apache Flink.

* Flink version: 1.20
* Scala version: 2.12.20
* Flink API: DataStream API
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application shows how to get runtime configuration, and sets up a Kinesis Data Stream source and a sink.

### Pre-requisites

You need to have `sbt` tool installed on you machine to build a Scala project. Use [steps from official guide](https://www.scala-sbt.org/download.html) to do that.

### Build

- Run `sbt assembly` to build an uber jar
- Use `target/scala-2.12/ScalaGettingStarted-flink_1.20.jar` in your MSF application

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink.

The following runtime properties are required:

| Group ID        | Key           | Description                                   |
|-----------------|---------------|-----------------------------------------------|
| `InputStream0`  | `stream.name` | Name of the input stream                      |
| `InputStream0`  | `aws.region`  | Region of the input stream, e.g. `us-east-1`  |
| `OutputStream0` | `stream.name` | Name of the output stream                     |
| `OutputStream0` | `aws.region`  | Region of the output stream, e.g. `us-east-1` |


### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../../java/running-examples-locally.md) for details.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator),
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.
