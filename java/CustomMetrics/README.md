# Custom Metrics

* Flink version: 1.18.1
* Flink API: DataStream API
* Language: Java (11)

This example demonstrate how to create your own metrics to track application-specific data, such as processing events or accessing external resources and publish it to Cloudwatch.

The custom metrics is explained by two different applications:
* **RecordCount** - This application shows how to create a custom record count metric and publish it to Cloudwatch.
* **WordCount** - This application shows how to create a custom word count metric and publish it to Cloudwatch.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`. They are all case-sensitive:
* `input.stream.name` - Kinesis Data Stream to be used for source.
* `output.stream.name` - Kinesis Data Stream to be used for source.
* `aws.region` - Kinesis Data Stream to be used for sink.
* `flink.stream.initpos` - Kinesis Data Streams starting position. Provide `LATEST` to start from the latest record of the stream.

### Running locally in IntelliJ
Update `PropertyMap` in configuration file with test resource names
([RecordCount](RecordCount/src/main/resources/flink-application-properties-dev.json) or [WordCount](WordCount/src/main/resources/flink-application-properties-dev.json)).

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
