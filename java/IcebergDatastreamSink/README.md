# Flink Iceberg Sink using DataStream API

* Flink version: 1.20.0
* Flink API: DataStream API
* Iceberg 1.6.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/) 
   and [Iceberg](https://iceberg.apache.org/docs/latest/flink/)

This example demonstrate how to use
[Flink Iceberg Sink Connector](https://iceberg.apache.org/docs/latest/flink-writes/) with the Glue Data Catalog.

For simplicity, the application generates synthetic data, random stock prices, internally. 
Data is generated as AVRO Generic Record, simulating a real source, for example a Kafka Source, that receives records 
serialized with AVRO.

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
| `Iceberg` | `partition.fields`       | `symbol`          | Comma separated list of partition fields.                                                                           |
| `Iceberg` | `sort.field`             | `timestamp`       | Sort field.                                                                                                         |
| `Iceberg` | `operation`              | `updsert`         | Iceberg operation. One of `upsert`, `append` or `overwrite`.                                                        |
| `Iceberg` | `upsert.equality.fields` | `symbol`          | Comma separated list of fields used for upsert. It must match partition fields. Required if `operation` = `upsert`. |


### Checkpoints

Checkpointing must be enabled. Iceberg commits writes on checkpoint.

When running locally, the application enables checkpoints programmatically, every 10 seconds.
When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.


### Known limitations

At the moment there are current limitations concerning Flink Iceberg integration:
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
