# Flink Iceberg Sink using DataStream API Examples

* Flink version: 1.15.2
* Flink API: DataStream API
* Iceberg 1.3.1
* Language: Java (11)

This example demonstrate how to use
[Flink Iceberg Sink Connector](https://iceberg.apache.org/docs/latest/flink-writes/) with the Glue Data Catalog

This samples consumes messages from a Kinesis Data Stream in JSON and writes them into an Iceberg Table in Amazon S3. 
It uses AVRO for
* Passing the schema of the table to the application
* Automatically converting input JSON messages into AVRO
* Converting the messages automatically into Iceberg table schema

AVRO is not used for serializing the input. It is mainly used to simplify the iceberg table creation and sink by getting the schema from AVRO.

At the moment there are current limitations concerning Flink Iceberg integration
* Doesn't support Iceberg Table with hidden partitioning
* Doesn't support adding columns, removing columns, renaming columns or changing columns.

**Note** : Iceberg in Flink requires Checkpointing to be enabled. Flink will commit the records whenever checkpoint is being triggered.

This example uses the Iceberg Flink Sink wrapper for Avro Generic Record. The schema used (`.avsc`) is provided in [./src/main/resources](./src/main/resources)

This example uses `FlinkKinesisConsumer` and Iceberg `FlinkSink`.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kinesis.source` Kinesis Data Streams source
* `kinesis.region` AWS Kinesis Region
* `iceberg.warehouse` S3 Bucket URI used for Iceberg Warehouse
* `iceberg.db` Glue Database (Needs to be created before)
* `iceberg.table` Iceberg Table Name
* `iceberg.partition.fields` Fields used for partitioning the table. (Comma separated fields, Ex. symbol,accountNr)
* `iceberg.sort.field` Field used for SortOrder in Iceberg Table
* `iceberg.operation` Operation to be done in Iceberg Table (append,upsert,overwrite)
* `iceberg.upsert.equality.fields` If doing upsert, fields to be used for performing upsert, it must match partition fields. (Comma separated fields, Ex. symbol,accountNr)

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.

You must have a Kinesis Data Stream running as well as a Glue Database in the Glue Data Catalog