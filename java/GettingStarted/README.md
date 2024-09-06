## Getting Started Flink Java project - DataStream API

Skeleton project for a basic Flink Java application to run on Amazon Managed Service for Apache Flink.

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Consumer, Kinesis Sink

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application shows how to get runtime configuration, and set up a Kinesis Data Stream source and a sink.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `InputStream0`  | `stream.name` | Name of the input stream  |
| `InputStream0`  | `aws.region`  | (optional) Region of the input stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |
| `OutputStream0` | `stream.name` | Name of the output stream |
| `OutputStream0`  | `aws.region`  | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |

All parameters are case-sensitive.

### Running in IntelliJ

To run the application locally, in IntelliJ:

1. Update `PropertyMap` in [configuration file](src/main/resources/flink-application-properties-dev.json).
2. Edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
3. Use the [AWS Toolkit](https://aws.amazon.com/intellij/) plugin to run the application with an AWS profile with access to the source and destination Kinesis Streams.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator), 
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.
