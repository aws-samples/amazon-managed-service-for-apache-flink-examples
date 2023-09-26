# Flink Kinesis Source & Sink examples (standard and EFO)

* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use [Flink Kinesis Connector](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kinesis/), source and sink.

It also shows how to set up an **Enhanced Fan-Out (EFO)** source.

This example uses [`FlinkKinesisConsumer` and `KinesisStreamsSink` connectors](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kinesis/).

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `kinesis.source.stream` Kinesis Data Stream to be used for source (default: `source`)
* `kinesis.sink.stream` Kinesis Data Stream to be used for sink (default: `destination`)
* `kinesis.region` AWS Region where Kinesis Data Streams are (default `eu-west-1`)

#### Enhanced Fan-Out (EFO) source

To use EFO for the Kinesis Data Stream source, set up two additional configuration parameters:

* `kinesis.source.type` Kinesis Data Stream publisher type, either `POLLING` or `EFO` (default: `POLLING`)
* `kinesis.source.efoConsumer` Kinesis Data Stream EFO Consumer name; only used if `kinesis.source.type=EFO` (default: `sample-efo-flink-consumer`)

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.


```
--kinesis.source.stream stream-input --kinesis.sink.stream stream-windowing-sliding-output --kinesis.region ap-south-1 --kinesis.source.type POLLING
```


## Running locally through MVN command line


```
 mvn clean compile exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.StreamingJob" \
 -Dexec.args="--kinesis.source.stream stream-input --kinesis.sink.stream stream-output --kinesis.region ap-south-1 --kinesis.source.type POLLING" 

```

*Kinesis - Sliding window* `kinesis.SlidingWindowStreamingJobWithParallelism` :

```
 mvn clean compile exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.windowing.kinesis.SlidingWindowStreamingJobWithParallelism" \
 -Dexec.args="--InputStreamRegion ap-south-1 --InputStreamName stream-input --OutputStreamRegion ap-south-1 --OutputStreamName stream-windowing-sliding-output" 

```