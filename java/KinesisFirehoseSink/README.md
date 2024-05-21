# Flink Kinesis Firehose Sink example

* Flink version: 1.18
* Flink API: DataStream API
* Language: Java (11)

This example demonstrate how to use [Flink Kinesis Firehose Sink Connector](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/firehose/).

This example uses the [`KinesisFirehoseSink`](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/firehose/) connector.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from a local file, when running locally.

All runtime properties are case-sensitive.

Configuration parameters:

* `stream.name` Amazon Data Firehose to be used for sink
* `aws.region` AWS Region where Amazon Data Firehose Stream is (e.g. "us-east-1")

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
