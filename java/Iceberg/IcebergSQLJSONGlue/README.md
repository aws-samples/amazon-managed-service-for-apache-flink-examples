Here's the rewritten README.md that matches the code example:

# Flink SQL Example with Glue Catalog and Iceberg

* Flink version: 1.20.0
* Flink API: SQL API
* Iceberg 1.6.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/)
  and [Iceberg](https://iceberg.apache.org/docs/latest/flink/)

This example demonstrates how to use Flink SQL API with Apache Iceberg and AWS Glue Catalog. The application generates synthetic stock price data using Flink's DataGen connector and writes it to an Iceberg table in the Glue Catalog.

### Prerequisites

The application expects the following resources:
* A Glue Data Catalog database in the current AWS region. The database name is configurable.
* An S3 bucket to store the Iceberg table data.

#### IAM Permissions

The application must have IAM permissions to:
* Show and alter Glue Data Catalog databases, show and create Glue Data Catalog tables.
  See [Glue Data Catalog permissions](https://docs.aws.amazon.com/athena/latest/ug/fine-grained-access-to-glue-resources.html).
* Read and Write from the S3 bucket.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from Runtime Properties.

When running locally, the configuration is read from the
`resources/flink-application-properties-dev.json` file.

Runtime parameters:

| Group ID  | Key             | Default                              | Description                                    |
|-----------|-----------------|--------------------------------------|------------------------------------------------|
| `DataGen` | `records.per.sec` | `10.0`                               | Records per second generated                   |
| `Iceberg` | `bucket.prefix`   | `s3://<<BUCKET-NAME>>/glue-example/` | S3 bucket prefix for Iceberg table storage    |
| `Iceberg` | `catalog.db`      | `iceberg`                            | Name of the Glue Data Catalog database         |
| `Iceberg` | `catalog.table`   | `stock_data`                         | Name of the Glue Data Catalog table           |

### Checkpoints

Checkpointing must be enabled for proper operation. When running locally, the application enables checkpoints programmatically every 30 seconds with a parallelism of 2. When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.

### Schema

The application creates a table with the following schema:
- price: DOUBLE
- ticker: STRING
- eventtime: TIMESTAMP(3)

The table is created using Flink SQL DDL and data is inserted using SQL INSERT statements.

### Running locally

You can run this example directly in a local environment with a web UI. The application will:
1. Create a local Flink environment with web UI
2. Set up the Glue catalog configuration
3. Generate synthetic stock price data
4. Create the database and table in Glue
5. Insert the generated data into the Iceberg table
6. Query and display the results

Make sure to configure the appropriate AWS credentials and region when running locally.

See [Running examples locally](../../running-examples-locally.md) for details.
