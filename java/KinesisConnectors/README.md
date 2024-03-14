# Flink Kinesis Source & Sink examples (standard and EFO)

* Flink version: 1.18
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use Flink Kinesis Connector source and sink.

It also shows how to set up an **Enhanced Fan-Out (EFO)** source.

This example uses `FlinkKinesisConsumer` and `KinesisStreamsSink` connectors.

![Flink Example](images/flink-kinesis-example.png)
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
To run this example locally -
* Create source and sink Kinesis streams. 
* Ensure that use profile is configured and user has required permission to read/write from Kinesis streams. 
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.

```
--kinesis.source.stream stream-input --kinesis.sink.stream stream-output --kinesis.region ap-south-1 --kinesis.source.type POLLING
```

Following is the screenshot of run configuration
![Run Configuration](images/runConfiguration.png)