## Sample illustrating how to use Side Outputs in Apache Flink
* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Connectors: [Datagen](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/datagen/), [Kinesis](https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/kinesis/)

This sample illustrates how to leverage **Side Outputs** in Apache Flink for splitting a stream on specified attributes. More details on Apache Flink's Side Outputs functionality can be found [here](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/side_output/). This pattern is particularly useful when trying to implement the concept of **Dead Letter Queues (DLQ)** in streaming applications.

In the code sample, data is generated from a Datagen Source connector. At random, the messages are either assigned the value of `Hello World` or `Poison`. 

The messages are then sent to a validation method where they are assigned a boolean value indicating whether or not the message can be processed (`Poison` messages cannot be processed in this case).

The **SingleOutputStreamOperator** class and the **getSideOutput** method are used to split the stream into a validated stream (`mainStream`) and a poison message stream (`exceptionStream`). Messages in each stream are then sent to their respective Kinesis Stream sinks.

### Set up Cloud Infrastructure

In order to run this application you will need to configure **two Kinesis Streams**: one for *successfully* processed messages and one for *unsuccessfully* processed messages (which will serve as the DLQ sink). Properties of these streams are to be recorded in the **Runtime Configuration**.



### Runtime Configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

Here is the JSON data in the desired table format, with personally identifiable information (PII) removed and replaced with `X's`:

| Group ID          | Key           | Description                                                                                                                                                  |
|-------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ProcessedOutputStream`    | `stream.arm` | ARN of the output stream for *successfully* processed messages.                                                                                                                                   |
| `ProcessedOutputStream`    | `aws.region`  | (optional) Region of the processed output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |
| `DLQOutputStream`    | `stream.arn` | ARN of the output stream for *unsuccessfully* processed messages ("Poison" messages).                                                                                                                                   |
| `DLQOutputStream`    | `aws.region`  | (optional) Region of the DLQ output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |



All parameters are case-sensitive.

This simple example assumes the Kinesis Streams are in the same region as the application, or in the default region for the authentication profile, when running locally.


### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.