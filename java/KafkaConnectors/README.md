## Flink Kafka Source & Sink Examples

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use
[Flink Kafka Connector](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/),
source and sink.

This example uses KafkaSource and KafkaSink.

This example expects a JSON payload as input and outputs a corresponding JSON output. 
The JSON input follows the structure set in `Stock.java` and can be automatically generated with
[`stock_kafka.py`](../../python/data-generator/stock_kafka.py) under the `python/data_generator` directory.

![Flink Example](images/flink-example.png),

Note that the old 
[`FlinkKafkaConsumer`](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/#kafka-sourcefunction)
and [`FlinkKafkaProducer`](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/kafka/#kafka-producer)
were removed in Flink 1.17 and 1.15, respectively.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID  | Key                 | Description                                 | 
|-----------|---------------------|---------------------------------------------|
| `Input0`  | `bootstrap.servers` | Source cluster boostrap servers.            |
| `Input0`  | `topic`             | Source topic (default: `source`).           |
| `Input0`  | `group.id`          | Source group id (default: `my-group`)       |
| `Output0` | `bootstrap.servers` | Destination cluster bootstrap servers.      |
| `Output0` | `topic`             | Destination topic (default: `destination`). |

If you are connecting with no-auth and no SSL, above will work. Else you need additional configuration for both source and sink.

#### For IAM Auth

When using IAM Auth, the following Runtime Properties are expected at the Group ID `AuthProperties`:
* `sasl.mechanism` = `AWS_MSK_IAM`
* `sasl.client.callback.handler.class` = `software.amazon.msk.auth.iam.IAMClientCallbackHandler`
* `sasl.jaas.config` = `software.amazon.msk.auth.iam.IAMLoginModule required;`
* `security.protocol` = `SASL_SSL`

The properties in the `AuthProperties` group are passed to both Kafka Source and Kafka Sink configurations.

### Data Generator

To generate the JSON data expected by this example, you can use the Python [data-generator](../../python/data-generator) 
script you can find in the Python examples.

Alternatively, you can use the [Flink DataGenerator](../FlinkDataGenerator) application, which generates the same schema.

### Running locally in IntelliJ

To run the example locally we provide a [docker-compose](docker/docker-compose.yml) file which starts a local Kafka cluster
with 3 brokers on `locakhost:9092`, `locakhost:9093`, and `locakhost:9094`.
It also runs a Kafka UI responding on `http://localhost:8080`. 

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

Note that you can run both the [Flink DataGenerator](../FlinkDataGenerator) and this example locally, in the  IDE, to observe 
data being consumed and re-published to Kafka.

See [Running examples locally](../running-examples-locally.md) for details.
