# S3 Sink

* Flink version: 1.19
* Flink API: DataStream API
* Language Java (11)

This example demonstrates how to write data into an Amazon S3 Bucket.

The Flink application uses a synthetic source to generate records, 
parses the records, and it sinks the results to a given S3 Bucket.

### Sample Output

The data gets written to the given S3 bucket.
Following is the sample output of `aws s3 ls --recursive s3a://<BUCKET-NAME>/output/` if the output gets written to `s3a://<BUCKET-NAME>/output/` -
```shell
2023-10-13 13:29:33         74 output/2023-10-13--13/_part-84f63a7b-ba44-46f9-96a7-8fdf248767c8-0_tmp_d252d9b4-6382-4d6f-9e65-3fa3f058e9c5
2023-10-13 13:29:33        109 output/2023-10-13--13/_part-a87f5ad1-b920-463d-a2bc-bfc3c8ee2c81-0_tmp_fd16d35a-1297-4eb0-98cd-25301b1d12ba
```

The sample content of file is -

```
{"eventTime":1715863231917,"ticker":"MSFT","price":85.92103591154482}
{"eventTime":1715863232397,"ticker":"AMZN","price":83.69439555402906}
{"eventTime":1715863232398,"ticker":"AMZN","price":85.15101593687162}
```

## Pre-requisites

In order for to have this sample running locally or in Amazon Managed Service For Apache Flink, you will need the following:

* Existing S3 Bucket (Please add your S3 Bucket Name in flink-application-properties-dev.json )

## Flink compatibility

**Note:** This project is compatible with Flink 1.18 and Amazon Managed Service for Apache Flink.

It uses the `FileSink` (as opposed to `StreamingFileSink`).

## Starting the Flink Job 

### Running locally

Steps to follow:
* Update `resources/flink-application-properties-dev.json`. Add Kinesis Data Stream Name , Stream Region and S3 path to write the data to. 
```shell
## Example
[
  {
    "PropertyGroupId": "bucket",
    "PropertyMap": {
      "name": "my-bucket-name"
    }
  }
]
```
* Execute using credentials with permissions write data into Amazon S3.

##### Running in IntelliJ
* To start the Flink job in IntelliJ edit the Run/Debug configuration enabling 'Add dependencies with "provided" scope to
the classpath'.