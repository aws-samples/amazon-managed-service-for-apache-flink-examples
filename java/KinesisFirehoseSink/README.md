# Flink Amazon Data Firehose Sink example

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Amazon Data Firehose Sink

This example demonstrate how to use [Flink Amazon Data Firehose Sink Connector](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/datastream/firehose/).

This example application generates random data and send the the outout to [`Amazon Data Firehose Sink`](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/connectors/datastream/firehose/) connector.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `OutputStream0` | `stream.name` | Name of the Amazon Data firehose output stream |
| `OutputStream0`  | `aws.region`  | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |


To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running locally in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
