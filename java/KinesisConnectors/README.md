# Flink Kinesis Source & Sink examples (standard and EFO)

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use Flink Kinesis Connector source and sink.

It also shows how to set up an **Enhanced Fan-Out (EFO)** source.

This example uses `FlinkKinesisConsumer` and `KinesisStreamsSink` connectors.

![Flink Example](images/flink-kinesis-example.png)
### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from a local file, when running locally.

All properties are case-sensitive.

Configuration parameters:

Configuration PropertyGroupId: `InputStream0`

* `stream.name` Kinesis Data Stream to be used for source
* `flink.stream.initpos` Kinesis Data Stream consumer initial position
* `flink.stream.initpos.timestamp` Kinesis Data Stream consumer initial position timestamp (only applicable if `flink.stream.initpos` is `AT_TIMESTAMP`)
* `aws.region` AWS Region where the Kinesis Data Stream is

Configuration PropertyGroupId: `InputStream0`

* `stream.name` Kinesis Data Stream to be used for sink
* `aws.region` AWS Region where the Kinesis Data Stream is

#### Enhanced Fan-Out (EFO) source

To use EFO for the Kinesis Data Stream source, set up two additional configuration parameters in the PropertyGroupId `InputStream0` :

* `flink.stream.recordpublisher` Kinesis Data Stream publisher type, either `POLLING` or `EFO` (default: `POLLING`)
* `flink.stream.efo.consumername` Kinesis Data Stream EFO Consumer name; only used if `flink.stream.recordpublisher=EFO` (default: `sample-efo-flink-consumer`)

### Running locally in IntelliJ
To run this example locally -
* Create source and sink Kinesis streams. 
* Ensure that use profile is configured and user has required permission to read/write from Kinesis streams. 
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.