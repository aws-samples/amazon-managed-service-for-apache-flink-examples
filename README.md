# Amazon Managed Service for Apache Flink Examples

Example applications in Java, Python, Scala and SQL for Amazon Managed Service for Apache Flink (formerly known as Amazon Kinesis Data Analytics), illustrating various aspects of Apache Flink applications, and simple "getting started" base projects.

## Table of Contents

### Java Examples

#### Getting Started
- [**Getting Started - DataStream API**](./java/GettingStarted) - Skeleton project for a basic Flink Java application using DataStream API
- [**Getting Started - Table API & SQL**](./java/GettingStartedTable) - Basic Flink Java application using Table API & SQL with DataStream API

#### Connectors
- [**Kinesis Connectors**](./java/KinesisConnectors) - Examples of Flink Kinesis Connector source and sink (standard and EFO)
- [**Kinesis Source Deaggregation**](./java/KinesisSourceDeaggregation) - Handling Kinesis record deaggregation in the Kinesis source
- [**Kafka Connectors**](./java/KafkaConnectors) - Examples of Flink Kafka Connector source and sink
- [**Kafka Config Providers**](./java/KafkaConfigProviders) - Examples of using Kafka Config Providers for secure configuration management
- [**DynamoDB Stream Source**](./java/DynamoDBStreamSource) - Reading from DynamoDB Streams as a source
- [**Kinesis Firehose Sink**](./java/KinesisFirehoseSink) - Writing data to Amazon Kinesis Data Firehose
- [**SQS Sink**](./java/SQSSink) - Writing data to Amazon SQS
- [**Prometheus Sink**](./java/PrometheusSink) - Sending metrics to Prometheus
- [**Flink CDC**](./java/FlinkCDC) - Change Data Capture examples using Flink CDC
- [**JdbcSink**](./java/JdbcSink) - Writes to a relational database executing upsert statements

#### Reading and writing files and transactional data lake formats
- [**Iceberg**](./java/Iceberg) - Working with Apache Iceberg and Amazon S3 Tables
- [**S3 Sink**](./java/S3Sink) - Writing JSON data to Amazon S3
- [**S3 Avro Sink**](./java/S3AvroSink) - Writing Avro format data to Amazon S3
- [**S3 Avro Source**](./java/S3AvroSource) - Reading Avro format data from Amazon S3
- [**S3 Parquet Sink**](./java/S3ParquetSink) - Writing Parquet format data to Amazon S3
- [**S3 Parquet Source**](./java/S3ParquetSource) - Reading Parquet format data from Amazon S3

#### Data Formats & Schema Registry
- [**Avro with Glue Schema Registry - Kinesis**](./java/AvroGlueSchemaRegistryKinesis) - Using Avro format with AWS Glue Schema Registry and Kinesis
- [**Avro with Glue Schema Registry - Kafka**](./java/AvroGlueSchemaRegistryKafka) - Using Avro format with AWS Glue Schema Registry and Kafka

#### Stream Processing Patterns
- [**Serialization**](./java/Serialization) - Serialization of record and state
- [**Windowing**](./java/Windowing) - Time-based window aggregation examples
- [**Side Outputs**](./java/SideOutputs) - Using side outputs for data routing and filtering
- [**Async I/O**](./java/AsyncIO) - Asynchronous I/O patterns with retries for external API calls
- [**Custom Metrics**](./java/CustomMetrics) - Creating and publishing custom application metrics
- [**Fetching credentials from Secrets Manager**](./java/FetchSecrets) - Dynamically fetching credentials from AWS Secrets Manager

#### Utilities
- [**Fink Data Generator (JSON)**](java/FlinkDataGenerator) - How to use a Flink application as data generator, for functional and load testing.

### Python Examples

#### Getting Started
- [**Getting Started**](./python/GettingStarted) - Basic PyFlink application Table API & SQL

#### Handling Python dependencies
- [**Python Dependencies**](./python/PythonDependencies) - Managing Python dependencies in PyFlink applications using `requirements.txt`
- [**Packaged Python Dependencies**](./python/PackagedPythonDependencies) - Managing Python dependencies packaged with the PyFlink application at build time

#### Connectors
- [**Datastream Kafka Connector**](./python/DatastreamKafkaConnector) - Using Kafka connector with PyFlink DataStream API
- [**Kafka Config Providers**](./python/KafkaConfigProviders) - Secure configuration management for Kafka in PyFlink
- [**S3 Sink**](./python/S3Sink) - Writing data to Amazon S3 using PyFlink
- [**Firehose Sink**](./python/FirehoseSink) - Writing data to Amazon Kinesis Data Firehose
- [**Iceberg Sink**](./python/IcebergSink) - Writing data to Apache Iceberg tables
- [**Hudi Sink**](./python/HudiSink) - Writing data to Apache Hudi tables

#### Stream Processing Patterns
- [**Windowing**](./python/Windowing) - Time-based window aggregation examples with PyFlink/SQL
- [**User Defined Functions (UDF)**](./python/UDF) - Creating and using custom functions in PyFlink

#### Utilities
- [**Data Generator**](./python/data-generator) - Python script for generating sample data to Kinesis Data Streams
- [**Local Development on Apple Silicon**](./python/LocalDevelopmentOnAppleSilicon) - Setup guide for local development of Flink 1.15 on Apple Silicon Macs (not required with Flink 1.18 or later)


### Scala Examples

#### Getting Started
- [**Getting Started - DataStream API**](./scala/GettingStarted) - Skeleton project for a basic Flink Scala application using DataStream API

### Infrastructure & Operations

- [**Auto Scaling**](./infrastructure/AutoScaling) - Custom autoscaler for Amazon Managed Service for Apache Flink
- [**Scheduled Scaling**](./infrastructure/ScheduledScaling) - Scale applications up and down based on daily time schedules
- [**Monitoring**](./infrastructure/monitoring) - Extended CloudWatch Dashboard examples for monitoring applications
- [**Scripts**](./infrastructure/scripts) - Useful shell scripts for interacting with Amazon Managed Service for Apache Flink control plane API

---

## Contributing

See [Contributing Guidelines](CONTRIBUTING.md#security-issue-notifications) for more information.

## License Summary

This sample code is made available under the MIT-0 license. See the LICENSE file.
