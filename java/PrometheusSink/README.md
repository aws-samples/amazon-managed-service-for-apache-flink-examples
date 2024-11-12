## Flink Prometheus sink connector example

This example demonstrate how to set up [Prometheus connector](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/prometheus/)
to write data to Prometheus.

> The Flink Prometheus connector is used to write processed **data** to Prometheus, using Prometheus as a time-series database.
> It cannot be used to export Flink **metrics**.

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Prometheus sink

The example application generates random data internally and write to [Amazon Managed Prometheus (AMP)](https://aws.amazon.com/prometheus/).

### Prerequisites

The following resources are required to run the example:

* An [Amazon Managed Prometheus (AMP)](https://aws.amazon.com/prometheus/) - default configuration
* (optionally) A Grafana dashboard, connected to the AMP workspace, to visualize the time series.

### IAM Permissions

Add the `AmazonPrometheusRemoteWriteAccess`  Policy to the IAM Role of the Managed Flink application.

Alternatively, give `aps:RemoteWrite` permissions to the AMP workspace.

### Data

The time series written to Prometheus have a single metric, called `temperature`, with two dimensions: `roomId` and `sensorId`.
Measurement are purely random.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key                 | Description                                                                                                                                   | 
|-----------------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `DataGen`       | `records.per.sec`   | (optional) Number of generated records per second. Default = 100.                                                                             
| `PrometheusSink` | `endpoint.url`      | URL of the Remote-Write endpoint of the Prometheus workspace                                                                                  |
| `PrometheusSink` | `aws.region`        | Region of AMP workspace. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |
| `PrometheusSink` | `max.request.retry` | Maximum retries in case AMP respond with a retriable error (e.g. throttling).                                                                 |


All parameters are case-sensitive.

## Running locally in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

