# Flink Kinesis Firehose Sink example

* Flink version: 1.15
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use [Flink Kinesis Firehose Sink Connector](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/firehose/).

This example uses [`FlinkKinesisConsumer` and `KinesisFirehoseSink` connectors](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kinesis/).

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kinesis.source.stream` Kinesis Data Stream to be used for source (default: `source`)
* `kinesis.firehose.sink.stream` Kinesis Data Firehose to be used for sink (default: `delivery`)
* `kinesis.region` AWS Region where Kinesis Data Streams are (default `eu-west-1`)

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
