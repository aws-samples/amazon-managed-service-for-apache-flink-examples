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

### Background and motivation

As of version `5.0.0`, `KinesisStreamsSource` does not support de-aggregation yet.

If the connector is used to consume a stream produced with KPL aggregation, the source is not able to deserialize the records out of the box.

This example shows how to implement de-aggregation in the deserialization schema.

In particular, this example uses a wrapper which can be used to add de-aggregation to potentially any implementation 
of `org.apache.flink.api.common.serialization.DeserializationSchema`.

Implementation:
[KinesisDeaggregatingDeserializationSchemaWrapper.java](flink-app/src/main/java/com/amazonaws/services/msf/deaggregation/KinesisDeaggregatingDeserializationSchemaWrapper.java)

> *IMPORTANT*: This implementation of de-aggregation is for demonstration purposes only.
> The code is not meant for production use and is not optimized in terms of performance.
