# Getting Started Flink Java project - Window aggregation

Example of project for a basic Flink Java application using data aggregation in Time based Windows.

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.
There is one sample application which demonstrates behaviour of different windows supported by Flink. 

The appllication generates random synthetic data internally, aggregates them with four different types of windowing aggregations and writes to four separate Kinesis Streams. 


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

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `OutputStream0` | `stream.name` | Name of the output stream |
| `OutputStream0`  | `aws.region`  | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |


To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

Update `PropertyMap` in [configuration file](src/main/resources/flink-application-properties-dev.json).

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to 
the classpath'*.

Use the [AWS Toolkit](https://aws.amazon.com/intellij/) plugin to run the application with an AWS profile with access to the destination Kinesis Streams.

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
