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


The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `bucket`        | `name`        | Name of the destination bucket, **without** the prefix "s3://" |
| `bucket`        | `path`        | Path withing the bucket the output will ebe written to, without the trailing "/" |

To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).


### Running in IntelliJ

Update `PropertyMap` in [configuration file](src/main/resources/flink-application-properties-dev.json).

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

Use the [AWS Toolkit](https://aws.amazon.com/intellij/) plugin to run the application with an AWS profile with access to the destination bucket.
