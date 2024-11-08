# DynamoDB Streams Source example

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: DynamoDb Streams Source


This example demonstrate how to use Flink DynamoDB Streams source.

This example uses the `DynamoDbStreamsSource` provided in Apache Flink's connector ecosystem.

### Pre-requisite set up

To run this example, the following resources needs to be created:
1. A DynamoDB table - the example uses a table schema documented using `@DynamoDbBean`. See `DdbTableItem`.
2. Set up DynamoDB Stream against the created table. See [DynamoDB Streams documentation](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html).
3. Add items to the DynamoDB table using the schema created via console.


### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or, when running locally, from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

All parameters are case-sensitive.

| Group ID        | Key           | Description               | 
|-----------------|---------------|---------------------------|
| `InputStream0`  | `stream.arn` | ARN of the input stream.  |

Every parameter in the `InputStream0` group is passed to the DynamoDB Streams consumer, for example `flink.stream.initpos`.

See Flink DynamoDB connector docs](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/dynamodb/) for details about configuring the DynamoDB connector.

To configure the application on Managed Service for Apache Flink, set up these parameter in the *Runtime properties*.

To configure the application for running locally, edit the [json file](resources/flink-application-properties-dev.json).

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

