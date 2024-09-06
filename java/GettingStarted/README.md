## Getting Started Flink Java project - DataStream API

Skeleton project for a basic Flink Java application to run on Amazon Managed Service for Apache Flink.

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Consumer, Kinesis Sink

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application shows how to get runtime configuration, and set up a Kinesis Data Stream source and a sink.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `InputStream0`  | `stream.name` | Name of the input stream  |
| `OutputStream0` | `stream.name` | Name of the output stream |

To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

This simple example assumes the Kinesis Streams is in the same region as the application, or in the default region for the authentication profile, when running locally.

### Running in IntelliJ

Update `PropertyMap` in [configuration file](src/main/resources/flink-application-properties-dev.json).

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to 
the classpath'*.

Use the [AWS Toolkit](https://aws.amazon.com/intellij/) plugin to run the application with an AWS profile with access to the source and destination Kinesis Streams.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator), 
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.
