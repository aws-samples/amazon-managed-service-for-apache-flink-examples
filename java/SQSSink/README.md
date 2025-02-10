# Flink SQS Sink example

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: SQS Sink

This example demonstrate how to use [SQS Sink Connector](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/sqs/).

This example application generates random data and send the output to [`SQS Sink`](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/sqs/) connector.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID       | Key          | Description                                                                                                                                                  | 
|----------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OutputQueue0` | `sqs-url`    | URL to SQS sink output queue                                                                                                                                 |
| `OutputQueue0` | `aws.region` | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |


To configure the applicaton on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running locally in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
