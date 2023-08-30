## Getting Started Flink Java project - Tumbling Window

Example of project for a basic Flink Java application using Tumbling Window.

* Flink version: 1.15.2
* Flink API: DataStream API
* Flink Connectors: Kafka Connector
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application reads from a Kafka source topic and writes to Kafka destination topic, showing how to implement a simple events count using tumbling window assigner. 

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kafka-source-topic` source topic
* `kafka-sink-topic` source topic
* `brokers` source Kafka cluster boostrap servers 

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

### Data generator

The project includes a [simple Python script](./data-generator/generator.py) that generates data and publishes
to Kafka. 
Edit the script to change the boostrap brokers and topic name.
