## Getting Started Flink Java project - Table API & SQL

Example of project for a basic Flink Java application using the Table API & SQL in combination with the DataStream API.

* Flink version: 1.20
* Flink API: Table API & SQL, and DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application generates random data using the DataStream [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/datagen/)
connector, Table API & SQL to filter and transform the data, and then write to S3 as JSON files.

The DataStream API DataGen connector is used instead of the Table API DataGen connector, because it allows fine-grained
control of the generated data. In this example we implemented a `GeneratorFunction` to generate plausible random stock prices.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `bucket`        | `name`        | Name of the destination bucket, **without** the prefix "s3://" |
| `bucket`        | `path`        | Path withing the bucket the output will be written to, without the trailing "/" |

All parameters are case-sensitive.

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
