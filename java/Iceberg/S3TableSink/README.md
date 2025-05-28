# Flink Iceberg Sink using DataStream API

* Flink version: 1.19.0
* Flink API: DataStream API
* Iceberg 1.8.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/) 
   and [Iceberg](https://iceberg.apache.org/docs/latest/flink/)

This example demonstrate how to use
[Flink Iceberg Sink Connector](https://iceberg.apache.org/docs/latest/flink-writes/) with S3 Tables.

For simplicity, the application generates synthetic data, random stock prices, internally. 
Data is generated as AVRO Generic Record, simulating a real source, for example a Kafka Source, that receives records 
serialized with AVRO.

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
| `DataGen` | `records.per.sec`        | `100.0`          | Records per second generated.                                                                                       |
| `Iceberg` | `table.bucket.arn`       | (mandatory)      | ARN of the S3 bucket, e.g., `arn:aws:s3tables:region:account-id:bucket/bucket-name`                                 |
| `Iceberg` | `catalog.db`             | `default`        | Name of the S3 table database.                                                                                      |
| `Iceberg` | `catalog.table`          | `prices_s3table` | Name of the S3 table.                                                                                               |
| `Iceberg` | `partition.fields`       | `symbol`         | Comma separated list of partition fields.                                                                           |
| `Iceberg` | `operation`              | `append`         | Iceberg operation. One of `upsert`, `append` or `overwrite`.                                                        |
| `Iceberg` | `upsert.equality.fields` | `symbol`         | Comma separated list of fields used for upsert. It must match partition fields. Required if `operation` = `upsert`. |

### Checkpoints

Checkpointing must be enabled. Iceberg commits writes on checkpoint.

When running locally, the application enables checkpoints programmatically, every 10 seconds.
When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.


### Known limitations

At the moment there are current limitations concerning Flink Iceberg integration with S3 Tables:
* * Currently, this example needs to be in Flink v1.19, v1.20 isn't supported with the S3 Table Sink yet.
* Doesn't support Iceberg Table with hidden partitioning
* Doesn't support adding columns, removing columns, renaming columns or changing columns.

### Schema and schema evolution

The application must "know" the AVRO schema on start. 
The schema cannot be dynamically inferred based on the incoming records, for example using a schema registry. 
This is due to a limitation of the Flink Iceberg integration, that requires knowing the table schema upfront.

This implementation does support schema evolution in the incoming data, as long as new schema versions are FORWARD compatible.
Schema changes are not propagated to Iceberg. 
As long as the schema of incoming records is FORWARD compatible, the application deserializes incoming records using
the schema it knows. Any new field in the incoming record is discarded.

In this example, the schema is loaded from a schema definition file, [price.avsc](./src/main/resources/price.avsc) embedded 
with the application. 
It is technically possible to fetch the schema on application start from an external source, like a schema registry or a
schema definition file in an S3 bucket. This is beyond the scope of this example.

### Running locally, in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](https://github.com/nicusX/amazon-managed-service-for-apache-flink-examples/blob/main/java/running-examples-locally.md) for details.
