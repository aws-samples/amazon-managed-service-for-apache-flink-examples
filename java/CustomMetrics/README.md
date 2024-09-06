# Custom Metrics

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink

This example demonstrates how to create your own metrics to track application-specific data, such as processing events or accessing external resources and publish it to Cloudwatch.

The applications generates data internally and writes to a Kinesis Stream.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `OutputStream0` | `stream.name` | Name of the output stream |

To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

This simple example assumes the Kinesis Stream is in the same region as the application, or in the default region for the authentication profile, when running locally.


### Running locally in IntelliJ

Update `PropertyMap` in [configuration file](src/main/resources/flink-application-properties-dev.json).

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.

Use the [AWS Toolkit](https://aws.amazon.com/intellij/) plugin to run the application with an AWS profile with access to the destination bucket.
