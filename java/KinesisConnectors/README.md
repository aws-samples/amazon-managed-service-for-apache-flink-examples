# Flink Kinesis Source & Sink Examples

* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use
[Flink Kinesis Connector](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kinesis/),
source and sink, with no authentication.

This example uses on `FlinkKinesisConsumer` and `KinesisStreamsSink`.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kinesis.source` Kinesis Data Stream to be used for source (default: `source`)
* `kinesis.sink` Kinesis Data Stream to be used for sink (default: `destination`)
* `kinesis.region` AWS Region where Kinesis Data Streams are (default `eu-west-1`)

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
