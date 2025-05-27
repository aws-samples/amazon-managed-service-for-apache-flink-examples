# Flink Iceberg Sink using SQL API with S3 Tables

* Flink version: 1.19.0
* Flink API: SQL API
* Iceberg 1.8.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/) 
   and [S3 Tables](https://docs.aws.amazon.com/s3/latest/userguide/s3-tables.html)

This example demonstrates how to use
[Flink SQL API with Iceberg](https://iceberg.apache.org/docs/latest/flink-writes/) and the Amazon S3 Tables Catalog.

For simplicity, the application generates synthetic data, random stock prices, internally. 
Data is generated as POJO objects, simulating a real source, for example a Kafka Source, that receives records 
that can be converted to table format for SQL operations.

### Prerequisites

#### Create a Table Bucket
The sample application expects the S3 Table Bucket to exist and to have the ARN in the local environment:
```bash
aws s3tables create-table-bucket --name flink-example
{
      "arn": "arn:aws:s3tables:us-east-1:111122223333:bucket/flink-example"

}
```

If you already did this, you can query to get the ARN like this:

```bash
aws s3tables list-table-buckets
```

This will show you the list of table buckets. Select the one you wish to write to and paste it into the config file in this project.

#### Create a Namespace in the Table Bucket (Database)
The sample application expects the Namespace in the Table Bucket to exist
```bash
aws s3tables create-namespace \
    --table-bucket-arn arn:aws:s3tables:us-east-1:111122223333:bucket/flink-example \ 
    --namespace default
```

#### IAM Permissions

The application must have IAM permissions to:
* Write and Read from the S3 Table

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from Runtime Properties.

When running locally, the configuration is read from the
[resources/flink-application-properties-dev.json](./src/main/resources/flink-application-properties-dev.json) file.

Runtime parameters:

| Group ID  | Key                      | Default          | Description                                                                                                         |
|-----------|--------------------------|------------------|---------------------------------------------------------------------------------------------------------------------|
| `DataGen` | `records.per.sec`        | `10.0`           | Records per second generated.                                                                                       |
| `Iceberg` | `table.bucket.arn`       | (mandatory)      | ARN of the S3 Tables bucket, for example `arn:aws:s3tables:region:account:bucket/my-bucket`.                       |
| `Iceberg` | `catalog.db`             | `iceberg`        | Name of the S3 Tables Catalog database.                                                                             |
| `Iceberg` | `catalog.table`          | `prices_iceberg` | Name of the S3 Tables Catalog table.                                                                                |

### Checkpoints

Checkpointing must be enabled. Iceberg commits writes on checkpoint.

When running locally, the application enables checkpoints programmatically, every 30 seconds.
When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.

### Known limitations

At the moment there are current limitations concerning Flink Iceberg integration:
* Doesn't support Iceberg Table with hidden partitioning
* Doesn't support adding columns, removing columns, renaming columns or changing columns.

### Running locally, in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

Make sure to configure the appropriate AWS credentials and region when running locally, and ensure the provided S3 Tables bucket ARN is valid and accessible.

See [Running examples locally](https://github.com/nicusX/amazon-managed-service-for-apache-flink-examples/blob/main/java/running-examples-locally.md) for details.