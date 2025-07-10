# Flink Iceberg Sink using SQL API

* Flink version: 1.20.1
* Flink API: SQL API
* Iceberg 1.9.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/) 
   and [Iceberg](https://iceberg.apache.org/docs/latest/flink/)

This example demonstrates how to use
[Flink SQL API with Iceberg](https://iceberg.apache.org/docs/latest/flink-writes/) and the Glue Data Catalog.

For simplicity, the application generates synthetic data, random stock prices, internally. 
Data is generated as POJO objects, simulating a real source, for example a Kafka Source, that receives records 
that can be converted to table format for SQL operations.

### Prerequisites

The application expects the following resources:
* A Glue Data Catalog database in the current AWS region. The database name is configurable (default: "default").
  The application creates the Table, but the Catalog must exist already.
* An S3 bucket to write the Iceberg table.

#### IAM Permissions

The application must have IAM permissions to:
* Show and alter Glue Data Catalog databases, show and create Glue Data Catalog tables. 
  See [Glue Data Catalog permissions](https://docs.aws.amazon.com/athena/latest/ug/fine-grained-access-to-glue-resources.html).
* Read and Write from the S3 bucket.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from Runtime Properties.

When running locally, the configuration is read from the
[resources/flink-application-properties-dev.json](./src/main/resources/flink-application-properties-dev.json) file.

Runtime parameters:

| Group ID  | Key                      | Default           | Description                                                                                                         |
|-----------|--------------------------|-------------------|---------------------------------------------------------------------------------------------------------------------|
| `DataGen` | `records.per.sec`        | `10.0`            | Records per second generated.                                                                                       |
| `Iceberg` | `bucket.prefix`          | (mandatory)       | S3 bucket prefix, for example `s3://my-bucket/iceberg`.                                                             |
| `Iceberg` | `catalog.db`             | `default`         | Name of the Glue Data Catalog database.                                                                             |
| `Iceberg` | `catalog.table`          | `prices_iceberg`  | Name of the Glue Data Catalog table.                                                                                |

### Checkpoints

Checkpointing must be enabled. Iceberg commits writes on checkpoint.

When running locally, the application enables checkpoints programmatically, every 30 seconds.
When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.

### Known limitations

At the moment there are current limitations concerning Flink Iceberg integration:
* Doesn't support Iceberg Table with hidden partitioning
* Doesn't support adding columns, removing columns, renaming columns or changing columns.

### Hadoop Library Availability Challenge

When integrating Flink with Iceberg, there's a common configuration challenge that affects most Flink deployments:

#### The Challenge
* When using Flink SQL's `CREATE CATALOG` statements, Hadoop libraries must be available on the system classpath
* However, standard Flink distributions use shaded dependencies that can create class loading conflicts with Hadoop's expectations
* This is particularly relevant for TaskManagers (which is the case for most generic Flink clusters, except EMR)

#### Solution Approaches
1. **For SQL Applications (This Example)**
   * If Hadoop is not pre-installed in the cluster, you'll need to create a custom HadoopUtils class and properly configure Maven dependencies
   * This example includes the necessary configuration to handle these dependencies

### Schema

The application uses a predefined schema for the stock price data with the following fields:
* `timestamp`: STRING - ISO timestamp of the record
* `symbol`: STRING - Stock symbol (e.g., AAPL, AMZN)
* `price`: FLOAT - Stock price (0-10 range)
* `volumes`: INT - Trade volumes (0-1000000 range)


### Running locally, in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](https://github.com/nicusX/amazon-managed-service-for-apache-flink-examples/blob/main/java/running-examples-locally.md) for details.
