# Flink Kinesis Source & Sink examples (standard and EFO)

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink


This example demonstrate how to use Flink Kinesis Connector source and sink.

It also shows how to set up an **Enhanced Fan-Out (EFO)** source.

This example uses `FlinkKinesisConsumer` and `KinesisStreamsSink` connectors.

This example expects a JSON payload as input and outputs a corresponding JSON output. 
The JSON input follows the structure set in `Stock.java` and can be automatically generated with
[`stock.py`](../../python/data-generator/stock.py) under the `python/data_generator` directory.

![Flink Example](images/flink-kinesis-example.png)

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key                             | Description                                                                                                                                                  | 
|-----------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InputStream0`  | `stream.arn`                    | ARN of the input stream.                                                                                                                                     |
| `InputStream0`  | `aws.region`                    | Region of the input stream.                                                                                                                                  |
| `InputStream0`  | `source.init.position`          | (optional) Starting position when the application starts with no state. Default is `LATEST`                                                                  |
| `InputStream0` | `source.reader.type`            | (optional) Choose between standard (`POLLING`) and Enhanced Fan-Out (`EFO`) consumer. Default is `POLLING`.                                                  |
| `InputStream0` | `source.efo.consumer.name`      | (optional, for EFO consumer mode only) Name of the EFO consumer. Only used if `source.reader.type=EFO`.                                                      |
| `InputStream0` | `source.efo.consumer.lifecycle` | (optional, for EFO consumer mode only) Lifecycle management mode of EFO consumer. Choose between `JOB_MANAGED` and `SELF_MANAGED`. Default is `JOB_MANAGED`. |
| `OutputStream0` | `stream.arn`                    | ARN of the output stream.                                                                                                                                    |
| `OutputStream0`  | `aws.region`                    | Region of the output stream.                                                                                                                                 |

Every parameter in the `InputStream0` group is passed to the Kinesis consumer, and every parameter in the `OutputStream0` is passed to the Kinesis client of the sink.

See Flink Kinesis connector docs](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/connectors/datastream/kinesis/) for details about configuring the Kinesis conector.

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator), 
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.

---

## Cross-account access to Kinesis

You can use the Kinesis source and sink to read and write to a Kinesis Stream in a different account, 
by configuring the connector to assume an IAM Role in the stream account.

This requires:
1. An IAM Role in the stream account with sufficient permissions to read or write the Kinesis stream, and allow the Managed Flink application account to assume this role.
2. Add to the Managed Flink application IAM role permissions to assume the previous role.
3. Configure the Kinesis source or sink to assume the role.

> Note: this approach also works with the legacy `FlinkKinesisConsumer`.

### IAM Role in the stream account

In the stream account, create a role with permissions to read or write the stream. 
See [Kinesis Data Streams documentation](https://docs.aws.amazon.com/streams/latest/dev/controlling-access.html#kinesis-using-iam-examples) for details.

Add a Trust Relationship to this role, allowing the application account to assume it:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::<application-account-id>:root"
            },
            "Action": "sts:AssumeRole",
            "Condition": {}
        }
    ]
}
```

### Application IAM Role

Add the following policy to the Managed Flink application role, allowing the application to assume the role in the stream account:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "Statement1",
            "Effect": "Allow",
            "Action": "sts:AssumeRole",
            "Resource": "arn:aws:iam::<stream-account-ID>:role/<role-in-stream-account-to-assume>"
        }
    ]
}
```

### Connector configuration

Pass the following configuration parameters to the Kinesis source or sink:

| Configuration                               | Value                                                                       | 
|---------------------------------------------|-----------------------------------------------------------------------------|
| `aws.credentials.provider`                  | `ASSUME_ROLE`                                                               |
| `aws.credentials.provider.role.arn`         | ARN of the role in the stream account                                       |
| `aws.credentials.provider.role.sessionName` | Any string used as name for the STS session. Must be unique in the account. |
