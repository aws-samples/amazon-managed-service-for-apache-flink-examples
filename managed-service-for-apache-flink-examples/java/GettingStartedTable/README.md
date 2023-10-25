## Getting Started Flink Java project - Table API & SQL

Example of project for a basic Flink Java application using the TableAPI or SQL.

* Flink version: 1.15
* Flink API: Table API or SQL
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

The application reads from a Kafka topic and writes to S3, showing how to implement a combination of DataStream API and
Table API, or SQL.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kafka-topic` source topic
* `brokers` source Kafka cluster boostrap servers 
* `s3Path` <s3-bucket>/<path> of the S3 destination , **without prepending `s3://`** 

### Running in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.

### Data generator

The project includes a [simple Python script](./data-generator/generator.py) that generates data and publishes
to Kafka. 
Edit the script to change the boostrap brokers and topic name.
