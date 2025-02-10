# Flink SQL Example with S3 Tables Catalog

* Flink version: 1.20.0
* Flink API: SQL API
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/)
  and S3 Tables

This example demonstrates how to use Flink SQL API with Amazon S3 Tables Catalog. The application generates synthetic stock price data using Flink's DataGen connector and writes it to a table in the S3 Tables Catalog.

### Prerequisites

The application expects the following resources:
* An S3 bucket configured for S3 Tables
* Appropriate AWS account and region configuration
* S3 Tables Catalog setup in your AWS account

#### IAM Permissions

The application must have IAM permissions to:
* Create and manage S3 Tables Catalog databases and tables
* Read and Write from the specified S3 bucket
* Access S3 Tables service endpoints

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from Runtime Properties.

When running locally, the configuration is read from the
`resources/flink-application-properties-dev.json` file.

Runtime parameters:

| Group ID  | Key                | Default                                                    | Description                                    |
|-----------|-------------------|------------------------------------------------------------|-------------------------------------------------|
| `DataGen` | `records.per.sec`  | `10.0`                                                     | Records per second generated                    |
| `Iceberg` | `table.bucket.arn` | `arn:aws:s3tables:<<region>>:<account>>:bucket/my-table-bucket` | ARN of the S3 Tables bucket                     |
| `Iceberg` | `catalog.db`       | `test_from_flink`                                          | Name of the S3 Tables Catalog database          |
| `Iceberg` | `catalog.table`    | `test_table`                                               | Name of the table to be created                 |

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
2. Set up the S3 Tables catalog configuration
3. Generate synthetic stock price data
4. Create the database and table in S3 Tables Catalog
5. Insert the generated data into the table
6. Query and display the results
7. Optionally cleanup resources (database and table)

Make sure to configure the appropriate AWS credentials and region when running locally, and ensure the provided S3 Tables bucket ARN is valid and accessible.