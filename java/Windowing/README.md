# Getting Started Flink Java project - Window aggregation

Example of project for a basic Flink Java application using data aggregation in Time based Windows.

* Flink version: 1.18
* Flink API: DataStream API
* Flink Connectors: Kinesis Connector
* Language: Java (11)

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.
There is one sample application which demonstrates behaviour of different windows supported by Flink. 

### Application structure

The application demonstrates data aggregation using variety of Flink Windows:
* Sliding Window based on processing time
* Sliding Window based on event time
* Tumbling Window based on processing time
* Tumbling Window based on event time

Application aggregates synthetic price data using above windows and emits results
into dedicated Kinesis Data Streams. 

### Sample Output
```
"AMZN",12.80,TimeWindow{start=123456789000, end=123456790000}"
```

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for
Apache Flink, or from local [configuration file](src/main/resources/flink-application-properties-dev.json), when running locally.

They are all case-sensitive.

Output stream are configured using dedicated PropertyGroup's (`OutputStream[0-3]`). Each of the streams requires following configuration properties:
* `aws.region`: region of the output stream
* `stream.name`: name of the output Kinesis Data Stream

## Running in IntelliJ
To run this example locally:

* Create sink Kinesis streams.
* Ensure that use profile is configured and user has required permission to read/write from Kinesis streams. 
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to
the classpath'*.
