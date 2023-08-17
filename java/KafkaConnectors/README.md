## Flink Kafka Source & Sink Examples

* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use
[Flink Kafka Connector](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kafka/),
source and sink, with no authentication.

This example uses on `KafkaSource` and `KafkaSink`.

Note that the old 
[`FlinkKafkaConsumer`](https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/connectors/datastream/kafka/#kafka-sourcefunction) 
and [`FlinkKafkaProducers`](https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/connectors/datastream/kafka/#kafka-producer)
are deprecated since Flink 1.15

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `source.bootstrap.servers` source cluster boostrap servers
* `source.topic` source topic (default: `source`)
* `sink.bootstrap.servers` sink cluster bootstrap servers
* `sink.topic` sink topic (default: `destination`)

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
