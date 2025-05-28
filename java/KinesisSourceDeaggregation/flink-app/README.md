## Flink job consuming an aggregated stream

This Flink job consumes a Kinesis stream with aggregated JSON records, and publish them to another stream.

### Prerequisites

The application expects two Kinesis Streams:
* Input stream containing the aggregated records
* Output stream where the de-aggregated records are published

The application must have sufficient permissions to read and write the streams.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID         | Key                             | Description                                                                                                                                                  | 
|------------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InputStream0`   | `stream.arn`                    | ARN of the input stream.                                                                                                                                     |
| `InputStream0`   | `aws.region`                    | Region of the input stream.                                                                                                                                  |
| `InputStream0`   | `source.init.position`          | (optional) Starting position when the application starts with no state. Default is `LATEST`                                                                  |
| `InputStream0`   | `source.reader.type`            | (optional) Choose between standard (`POLLING`) and Enhanced Fan-Out (`EFO`) consumer. Default is `POLLING`.                                                  |
| `InputStream0`   | `source.efo.consumer.name`      | (optional, for EFO consumer mode only) Name of the EFO consumer. Only used if `source.reader.type=EFO`.                                                      |
| `InputStream0`   | `source.efo.consumer.lifecycle` | (optional, for EFO consumer mode only) Lifecycle management mode of EFO consumer. Choose between `JOB_MANAGED` and `SELF_MANAGED`. Default is `JOB_MANAGED`. |
| `OutputStream0`  | `stream.arn`                    | ARN of the output stream.                                                                                                                                    |
| `OutputStream0`  | `aws.region`                    | Region of the output stream.                                                                                                                                 |

Every parameter in the `InputStream0` group is passed to the Kinesis consumer, and every parameter in the `OutputStream0` is passed to the Kinesis client of the sink.

See Flink Kinesis connector docs](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/kinesis/) for details about configuring the Kinesis connector.

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../../running-examples-locally.md) for details.


### Data Generator

Use the [KPL Producer](../kpl-producer) to generate aggregates StockPrices to the Kinesis stream


### Data example

```
{'event_time': '2024-05-28T19:53:17.497201', 'ticker': 'AMZN', 'price': 42.88}
```