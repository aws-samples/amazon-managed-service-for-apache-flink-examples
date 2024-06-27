# Flink Kafka Source & Sink Examples

* Flink version: 1.19
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use
[Flink Kafka Connector](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/),
source and sink.

This example uses `KafkaSource` and `KafkaSink`.

![Flink Example](images/flink-example.png),

Note that the old 
[`FlinkKafkaConsumer`](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/#kafka-sourcefunction)
and [`FlinkKafkaProducer`](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/#kafka-producer)
were removed in Flink 1.17 and 1.15, respectively.

## Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group IDs `Input0` and `Output0` (see [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json)).

All properties are case-sensitive.

Configuration parameters:

For the source (i.e. Group ID `Input0`):
* `bootstrap.servers` source cluster boostrap servers
* `topic` source topic (default: `source`)
* `group.id` source group id (default: `my-group`)

For the sink (i.e. Group ID `Output0`):
* `bootstrap.servers` sink cluster bootstrap servers
* `topic` sink topic (default: `destination`)
* `transaction.timeout.ms` sink transaction timeout (default: `1000`)

If you are connecting with no-auth and no SSL, above will work. Else you need additional configuration for both source and sink.

### For IAM Auth

When using IAM Auth, the following Runtime Properties are expected at the Group ID `AuthProperties`:
* `sasl.mechanism` AWS_MSK_IAM
* `sasl.client.callback.handler.class` software.amazon.msk.auth.iam.IAMClientCallbackHandler
* `sasl.jaas.config` "software.amazon.msk.auth.iam.IAMLoginModule required;"
* `security.protocol` SASL_SSL
* `ssl.truststore.location` /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts
* `ssl.truststore.password` changeit


## Running locally in IntelliJ

To run this example locally - 
* Run a Kafka cluster locally. You can refer https://kafka.apache.org/quickstart to download and start Kafka locally.
* Create `source` and `sink` topics. 
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
* Update [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json)
* Execute using credentials with permissions to consume data from a Kinesis Data Stream and write data into Amazon S3.
