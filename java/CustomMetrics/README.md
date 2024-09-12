# Custom Metrics

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink

This example demonstrates how to create your own metrics to track application-specific data, such as processing events or accessing external resources and publish it to Cloudwatch.

The applications generates data internally and writes to a Kinesis Stream.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `OutputStream0` | `stream.name` | Name of the output stream |
| `OutputStream0`  | `aws.region`  | (optional) Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |

All parameters are case-sensitive.

This simple example assumes the Kinesis Stream is in the same region as the application, or in the default region for the authentication profile, when running locally.


### Running locally in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
