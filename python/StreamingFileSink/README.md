## Example of writing to S3 in PyFlink 

* Flink version: 1.15.2
* Flink API: SQL/Table API
* Language: Python

Sample application reading data from Kinesis Data Streams, applying some windowing aggregation, and then writing to S3 as csv.

### Runtime configuration

* Local development: reads [application_properties.json](./application_properties.json)
* Deployed on Amazon Managed Service for Apache Fink: set up Runtime Properties, using Groupd ID and property names based on the content of [application_properties.json](./application_properties.json)

### Local testing - adding file system support for S3 buckets

In order to test S3 file sink locally, please add S3 file system plugin to PyFlink `lib` directory.

1. Download S3 file system implementation such as S3 FS Hadoop from Maven repository [here](https://mvnrepository.com/artifact/org.apache.flink/flink-s3-fs-hadoop). (Please pick a version that matches your apache-flink version.)
2. Copy the downloaded jar file (e.g. flink-s3-fs-hadoop-1.15.2.jar) to PyFlink `lib` directory.
   1. For miniconda3, the directory is at `~/miniconda3/envs/local-kda-env/lib/python3.8/site-packages/pyflink/lib/`

### Sample data

Use the [Python script](../data-generator/) to generate sample stock data to Kinesis Data Stream.
