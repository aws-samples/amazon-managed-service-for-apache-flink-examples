## KinesisStreamsSource de-aggregation

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Source and Sink

This example demonstrates how to consume records published using KPL aggregation using `KinesisStreamsSource`.

This folder contains two separate modules:
1. [kpl-producer](kpl-producer): a simple command line Java application to produce the JSON record to a Kinesis Stream, using KPL aggregation
2. [flink-app](flink-app): the Flink application demonstrating how to consume the aggregated stream, and publishing the de-aggregated records to another stream.

Look at the instructions in the subfolders to run the KPL Producer (data generator) and the Flink application.

### Background

As of version `5.0.0` the new `KinesisStreamsSource` does not support de-aggregation yet.
If the producer to the stream is using KPL aggregation the source is not able to deserialize the records out of the box.

However, it is possible to implement de-aggregation in the DeserializationSchema used to deserialize the records received.

This examples shows how to implement a wrapper which adds de-aggregation to any `DeserializationSchema`.

Implementation of the wrapper:
[KinesisDeaggregatingDeserializationSchemaWrapper.java](flink-app/src/main/java/com/amazonaws/services/msf/deaggregation/KinesisDeaggregatingDeserializationSchemaWrapper.java)

In this example, the wrapper is used with `JsonDeserializationSchema`. 
The wrapper can be  used with any other implementation of `org.apache.flink.api.common.serialization.DeserializationSchema`.

> *IMPORTANT*: This code implementing the de-aggregation is for demonstration purposes only.
> It is not meant to be used in production and is not necessarily optimized in terms of performance.
