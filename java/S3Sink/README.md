# S3 Sink

* Flink version: 1.18
* Flink API: DataStream API
* Language Java (11)

This example demonstrates how to write data coming from a Kinesis Data Stream into an Amazon S3 Bucket.

The Flink application consumes data in String format from a Kinesis Data Streams, 
parses the JSON and performs a count of number of products processed in a Tumbling Window of 1 minute, 
using processing time. It sinks the results to an S3 Bucket.

![Flink Example](images/flink-kinesis-s3.png)

### Sample Input
```json
{"price": 36, "product":"AMZN"}
```
### Sample Output

The data gets written to S3 path.
Following is the sample output of `aws s3 ls --recursive s3://<BUCKET-NAME>/flink/msf/` if the output gets written to `s3://<BUCKET-NAME>/flink/msf/` -
```shell
2023-10-13 13:29:33         74 flink/msf2/2023-10-13--13/_part-84f63a7b-ba44-46f9-96a7-8fdf248767c8-0_tmp_d252d9b4-6382-4d6f-9e65-3fa3f058e9c5
2023-10-13 13:29:33        109 flink/msf2/2023-10-13--13/_part-a87f5ad1-b920-463d-a2bc-bfc3c8ee2c81-0_tmp_fd16d35a-1297-4eb0-98cd-25301b1d12ba
```

The sample content of file is -

```
"AMZN" count: 2

"IBM" count: 3

"INFY" count: 2
```

## Pre-requisites

In order for to have this sample running locally or in Amazon Managed Service For Apache Flink, you will need the following:

* Existing Kinesis Data Stream (Please add Kinesis Data Stream Name and Region in flink-application-properties-dev.json)
* Existing S3 Bucket (Please add your S3 Bucket Name, including path to which you want the application to write the results, in flink-application-properties-dev.json )
* JSON producer, for which one of the fields is "product"

## Flink compatibility

**Note:** This project is compatible with Flink 1.18 and Amazon Managed Service for Apache Flink.

It uses the `FlinkKinesisConsumer` and  `FileSink` (as opposed to `StreamingFileSink`).

## Starting the Flink Job 

### Running locally

Steps to follow:
* Update `resources/flink-application-properties-dev.json`. Add Kinesis Data Stream Name , Stream Region and S3 path to write the data to. 
```shell
## Example
[
  {
    "PropertyGroupId": "FlinkApplicationProperties",
    "PropertyMap": {
      "input.stream": "stream-input",
      "stream.region": "ap-south-1",
      "s3.path": "s3://aksh-flink-sink/flink/msf/"
    }
  }
]
```
* Execute using credentials with permissions to consume data from a Kinesis Data Stream and write data into Amazon S3.

##### Running in IntelliJ
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling 'Add dependencies with "provided" scope to
the classpath'.
* Following is the screenshot of run configuration
![Run Configuration](images/runConfiguration.png)

##### Running via Maven command line
Execute following command from the project home directory -

```
 mvn clean compile exec:java  -Dexec.classpathScope="compile" \
 -Dexec.mainClass="com.amazonaws.services.msf.StreamingJob" 
```

### Deploying to Amazon Managed Service for Apache Flink

Follow instructions found in [cloudformation](cloudformation) directory to deploy and run the application using AWS Managed Service for Apache Flink.

## Sample data generation
To generate test data you can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator),
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.

RecordTemplate:
```json
{
"price": {{random.number.float({"min":1,"max":99,"precision": 0.01})}}, 
"product":"{{random.arrayElement(["AAPL","AMZN","MSFT","INTC","TBV"])}}"
}
```
