# S3 Sink

* Flink version: 1.15.2
* Flink API: DataStream API
* Language Java (11)

This example demonstrates how to write data coming from a Kinesis Data Stream into an Amazon S3 Bucket.

This example uses data generated from the [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator)

The Flink application consumes data in String format from a Kinesis Data Streams, 
parses the JSON and performs a count of number of products processed in a Tumbling Window of 1 minute, 
using processing time. It sinks the results to an S3 Bucket.

## Pre-requisites

In order for to have this sample running locally or in Amazon Managed Service For Apache Flink, you will need the following:

* Existing Kinesis Data Stream (Please add Kinesis Data Stream Name and Region in flink-application-properties-dev.json)
* Existing S3 Bucket (Please add your S3 Bucket Name, including path to which you want the application to write the results, in flink-application-properties-dev.json )
* JSON producer, for which one of the fields is "Product"

You can modify the Flink Application, if you wish to perform the count on a different field.

## Flink compatibility

**Note:** This project is compatible with Flink 1.15+ and Kinesis Data Analytics for Apache Flink.

### Flink API compatibility

It uses the `FlinkKinesisConsumer` and  `FileSink` (as opposed to `StreamingFileSink`).


### FileSink & S3 dependencies
The following dependencies related to FileSink Connector and Amazon S3 are included (for FLink 1.15.2):

1. `org.apache.flink:flink-connector-files:1.15.2` - File Sink Connector
2. `org.apache.flink:flink-s3-fs-hadoop:1.15.2` - Support for writing to Amazon S3.

### Running in Intellij

For running this example locally, you will need to make additional changes in the configuration. 
The Hadoop Dependencies are not capable of capturing the AWS Credentials locally in Intellij, for which you will need to provide your Access Key and Secret Access Key in a core-site.xml file.

Steps to follow:
* Update core-site.xml file with AWS Credentials that have permissions to consume data from a Kinesis Data Stream and write data into Amazon S3
* Update the flink-conf.yaml and add the path to the core-site.xml file in `fs.hdfs.hadoopconf`
* You will need to add the following environmental variable before starting the Flink job, please update with path to your flink-conf.file
  * FLINK_CONF_DIR= `Path to flink-conf.file`

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling 'Add dependencies with "provided" scope to
the classpath'.




