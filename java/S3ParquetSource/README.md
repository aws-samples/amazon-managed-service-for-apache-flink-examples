# S3 Parquet Source

* Flink API: DataStream API
* Language Java (11)
* Connectors: FileSystem Source and Kinesis Sink

This example demonstrates how to read Parquet files from S3.

The application reads records written by the [S3ParquetSink](../S3ParquetSink) example application, from an S3 bucket and publish them to Kinesis as JSON.

The records read from Parquet are deserialized into AVRO specific objects.

## Important note about reading Parquet

Flink relies on Hadoop libraries to read Parquet files from S3.
Because of the [Flink classloading system](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/debugging/debugging_classloading/) introduced after Flink 1.5, 
and because Flink S3 File System is installed in the cluster to support checkpointing, reading Parquet files normally causes a class not found exception in some Hadoop classes,
even if you include these classes in the uber-jar.

This examples demonstrates a workaround to this issue. The `org.apache.flink.runtime.util.HadoopUtils` class is replaced 
by a custom implementation, and some Hadoop classes are shaded (remapped) by the `maven-shade-plugin` used to build the uber-jar.

Note that this workaround works in this specific case and may not help in other cases of Hadoop class conflict you may encounter.

## Prerequisites

* An S3 bucket containing the data. The application IAM Role must allow reading from the bucket
* A Kinesis Data Stream to output the data. The application IAM Role must allow publishing to the stream

## Runtime Configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key                            | Description                                                                     | 
|-----------------|--------------------------------|---------------------------------------------------------------------------------|
| `InputBucket`   | `bucket.name`                  | Name of the destination S3 bucket.                                              |
| `InputBucket`   | `bucket.path`                  | Base path within the bucket.                                                    |
| `InputBucket`   | `discovery.interval.seconds`   | Inteval the bucket path is scanned for new files, in seconds (default = 30 sec) |
| `OutputStream0` | `stream.arn`                   | ARN of the output stream.                                                       |
| `OutputStream0` | `aws.region`                   | Region of the output stream.                                                    |

Every parameter in the `OutputStream0` is passed to the Kinesis client of the sink.

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

Note: when running locally, the application also prints the records to the console. 
Records starts appearing about 30 seconds after the application is fully initialized and starts reading from S3.

### Generating data

You can use the [S3ParquetSink](../S3ParquetSink) example application to write Parquet data into an S3 bucket.
Both examples use the same AVRO schema.

## AVRO Specific Record usage

The records read from Parquet are deserialized into AVRO specific objects.

The AVRO reader's schema definition (`avdl` file) is included as part of the source code in the `./src/main/resources/avro` folder.
The AVRO Maven Plugin is used to generate the Java object `StockPrice` at compile time.

If IntelliJ cannot find the `StockPrice` class when you import the project:
1. Run `mvn generate-sources` manually once
2. Ensure that the IntelliJ module configuration, in Project settings, also includes `target/generated-sources/avro` as Sources. If IntelliJ does not auto-detect it, add the path manually

These operations are only needed once.
