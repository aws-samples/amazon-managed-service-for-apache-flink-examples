# S3 Avro Source


* Flink API: DataStream API
* Language Java (11)
* Connectors: FileSystem Source and Kinesis Sink

This example demonstrates how to read AVRO files from S3.

The application reads AVRO records written by the [S3AvroSink](../S3AvroSink) example application, from an S3 bucket and publish them to Kinesis as JSON.

## Prerequisites

* An S3 bucket containing the data. The application IAM Role must allow reading from the bucket
* A Kinesis Data Stream to output the data. The application IAM Role must allow publishing to the stream

## Runtime Configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key            | Description                   | 
|-----------------|----------------|-------------------------------|
| `InputBucket`   | `bucket.name`  | Name of the source S3 bucket. |
| `InputBucket`   | `bucket.path`  | Base path within the bucket.  |
| `OutputStream0` | `stream.arn`   | ARN of the output stream.     |
| `OutputStream0` | `aws.region`   | Region of the output stream.  |

Every parameter in the `OutputStream0` is passed to the Kinesis client of the sink.

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

### Generating data

You can use the [S3AvroSink](../S3AvroSink) example application to write AVRO data into an S3 bucket. 
Both examples use the same AVRO schema.

## AVRO Specific Record usage

The AVRO reader's schema definition (`avdl` file) is included as part of the source code in the `./src/main/resources/avro` folder.
The AVRO Maven Plugin is used to generate the Java object `StockPrice` at compile time.

If IntelliJ cannot find the `StockPrice` class when you import the project:
1. Run `mvn generate-sources` manually once
2. Ensure that the IntelliJ module configuration, in Project settings, also includes `target/generated-sources/avro` as Sources. If IntelliJ does not auto-detect it, add the path manually

These operations are only needed once.
