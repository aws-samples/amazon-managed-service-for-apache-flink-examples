## Getting Started Flink Java project - Table API & SQL

Example of project for a basic Flink Java application using the Table API & SQL in combination with the DataStream API.

* Flink version: 1.19
* Flink API: Table API & SQL, and DataStream API
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application generates random data using the DataStream [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/datagen/)
connector, Table API & SQL to filter and transform the data, and then write to S3 as JSON files.

The DataStream API DataGen connector is used instead of the Table API DataGen connector, because it allows fine-grained
control of the generated data. In this example we implemented a `GeneratorFunction` to generate plausible random stock prices.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.

Command line parameters should be prepended by `--`.

Configuration parameters:

* `s3Path` <s3-bucket>/<path> of the S3 destination , **without** the prefix `s3://`

They parameters all case-sensitive.

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.
