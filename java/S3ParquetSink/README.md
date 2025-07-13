# S3 Parquet Sink

* Flink version: 1.20
* Flink API: DataStream API
* Language Java (11)
* Connectors: FileSystem Sink (and DataGen connector)

This example demonstrates how to write Parquet files to S3.
See the [S3 Parquet Source](../S3ParquetSource) example to read Parquet files from S3.

The example generates random stock price data using the DataGen connector and writes to S3 as Parquet files with 
a bucketing in the format `year=yyyy/month=MM/day=dd/hour=HH/` and rotating files on checkpoint.

Note that FileSystem sink commit the writes on checkpoint. For this reason, checkpoint are programmatically enabled when running locally.
When running on Managed Flink checkpoints are controlled by the application configuration and enabled by default.


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
