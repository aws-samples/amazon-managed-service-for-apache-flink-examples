# S3 Avro Sink

* Flink version: 1.20
* Flink API: DataStream API
* Language Java (11)
* Connectors: FileSystem Sink (and DataGen connector)

This example demonstrates how to write AVRO files to S3.

The example generates random stock price data using the DataGen connector and writes to S3 as AVRO files with
a bucketing in the format `year=yyyy/month=MM/day=dd/hour=HH/` and rotating files on checkpoint.

Note that FileSystem sink commit the writes on checkpoint. For this reason, checkpoint are programmatically enabled when running locally.
When running on Managed Flink checkpoints are controlled by the application configuration and enabled by default.

This application can be used in combination with the [S3AvroSource](../S3AvroSource) example application which read AVRO data with the same schema from S3.

## Prerequisites

* An S3 bucket for writing data. The application IAM Role must allow writing to the bucket


## Runtime Configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID       | Key           | Description                        | 
|----------------|---------------|------------------------------------|
| `OutputBucket` | `bucket.name` | Name of the destination S3 bucket. |
| `OutputBucket` | `bucket.path` | Base path withing the bucket.      |

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

## AVRO Specific Record usage

The AVRO schema definition (`avdl` file) is included as part of the source code in the `./src/main/resources/avro` folder.
The AVRO Maven Plugin is used to generate the Java object `StockPrice` at compile time.

If IntelliJ cannot find the `StockPrice` class when you import the project:
1. Run `mvn generate-sources` manually once
2. Ensure that the IntelliJ module configuration, in Project settings, also includes `target/generated-sources/avro` as Sources. If IntelliJ does not auto-detect it, add the path manually

These operations are only needed once.
