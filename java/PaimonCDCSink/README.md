## Flink Apache Paimon Sink using DataStream API

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Apache Paimon: 1.0.1
* Flink connectors: Flink CDC-MySQL / PostgreSQL / MongoDB / Kafka

This example demonstrates how to use Apache Paimon CDC ingestion components(MySQL / PostgreSQL / MongoDB / Kafka) to sink
data to Amazon S3 with Apache Paimon table format. The Apache Paimon Hive Catalog can work with Glue Data Catalog.

The project can run both on Amazon Managed Service for Apache Flink, and locally for development.

### Prerequisites
* A database source(MySQL, PostgreSQL, MongoDB) with binlog enabled or Kakfa / Amazon MSK source with Apache Paimon 
  supported CDC format(Canal CDC, Debezium CDC, Maxwell CDC, OGG CDC, JSON, aws-dms-json ) data streamed in it.
* If you want to use Apache Paimon Hive catalog with Glue Data Catalog, please install aws-glue-datacatalog-hive3-client 
  jar file into your local maven repo(please refer this [github repo](https://github.com/awslabs/aws-glue-data-catalog-client-for-apache-hive-metastore) to install or
  you can find this jar file in EMR Cluster and install it into your local maven repo) and copy your EMR cluster's `hive-site.xml` file into the project and repackage the project. 
* An S3 bucket to write the Paimon table.


#### IAM Permissions

The application must have IAM permissions to:
* Show and alter Glue Data Catalog databases, show and create Glue Data Catalog tables.
  See [Glue Data Catalog permissions](https://docs.aws.amazon.com/athena/latest/ug/fine-grained-access-to-glue-resources.html).
* Read and Write from the S3 bucket.


### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

This example parses runtime parameters according to the following rules and passes the parsed parameters to Apache Paimon Actions.

- The Paimon CDC ingestion action name is parsed from the key named action in the 'ActionConf' parameter group.
- Some global or common parameters can be placed in the 'ActionConf' parameter group. The parameter names should refer to the specific ingestion [action name](https://paimon.apache.org/docs/1.0/cdc-ingestion/overview/).
- For parameters like 'table_conf' and 'catalog_conf' that are set in the format of Key=Value, the name of the parameter group can be customized, such as “TableConf” or “CatalogConf”. 
For specific parameter names within the parameter group, they should follow the format “parameter group name@_parameter Key”, 
such as “table_conf@_bucket”, and the parameter value should be the corresponding Value.


Runtime parameters(Sample):

| Group ID      | Key                                        | Description                                                                            | 
|---------------|--------------------------------------------|----------------------------------------------------------------------------------------|
| `ActionConf`  | `action`                                   | Name of Apache Paimon CDC ingestion, `kafka_sync_database`, `mysql_sync_database` etc. |
| `ActionConf`  | `database`                                 | Target Paimon database name.                                                           |
| `ActionConf`  | `primary_keys`                             | (Optional) The primary keys for Paimon table                                           |
| `KafkaConf`   | `kafka_conf@_properties.bootstrap.servers` | Bootstrap servers of the Kafka Cluster.                                                |
| `KafkaConf`   | `kafka_conf@_properties.auto.offset.reset` | Offset of the Kafka Consumer                                                           |
| `KafkaConf`   | `kafka_conf@_properties.group.id`          | Consumer group Id                                                                      |
| `CatalogConf` | `catalog_conf@_metastore.client.class`     | Paimon Hive Catalog metastore client class name                                        |
| `CatalogConf` | `...`                                      | ...                                                                                    |
| `TableConf`   | `table_conf@_bucket`                       | Bucket of Paimon table                                                                 |
| `TableConf`   | `...`                                      | ...                                                                                    |

All parameters are case-sensitive.

### Samples
**Create an MSF application**

First, compile and package the application using Maven, then copy the packaged jar file to your s3.

```shell
mvn clean package -P KafkaCDC
```

Second, prepare an input json file to create a MSF application, you can add required information(like VPC, Subnets,Security.etc.) into this json file.

**Notice:** Your service execution role should have appropriate permissions, like s3 bucket access and glue access if you want to use Glue Data Catalog as Paimon Hive Catalog.
```json
{
  "ApplicationName": "kafka-cdc-paimon",
  "ApplicationDescription": "Sink CDC from Kafka as Apache Paimon table",
  "RuntimeEnvironment": "FLINK-1_20",
  "ServiceExecutionRole": "Your service role arn",
  "ApplicationConfiguration": {
    "ApplicationCodeConfiguration": {
      "CodeContent": {
        "S3ContentLocation": {
          "BucketARN": "Your bucket arn",
          "FileKey": "Your jar file s3 key"
        }
      },
      "CodeContentType": "ZIPFILE"
    },
    "EnvironmentProperties": {
      "PropertyGroups": [
        {
          "PropertyGroupId": "ActionConf",
          "PropertyMap": {
            "action": "kafka_sync_database",
            "database": "Your Paimon Database",
            "warehouse": "Your paimon warehouse path"
          }
        },
        {
          "PropertyGroupId": "KafkaConf",
          "PropertyMap": {
            "kafka_conf@_properties.bootstrap.servers": "MSK bootstrap servers",
            "kafka_conf@_properties.auto.offset.reset": "earliest",
            "kafka_conf@_properties.group.id": "group id",
            "kafka_conf@_topic": "Your cdc topic",
            "kafka_conf@_value.format": "debezium-json"
          }
        },
        {
          "PropertyGroupId": "CatalogConf",
          "PropertyMap": {
            "catalog_conf@_hadoop.fs.s3.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem",
            "catalog_conf@_hadoop.fs.s3.buffer.dir": "/var/tmp"
          }
        },
        {
          "PropertyGroupId": "TableConf",
          "PropertyMap": {
            "table_conf@_bucket": "4",
            "table_conf@_metadata.iceberg.storage": "hive-catalog",
            "table_conf@_metadata.iceberg.manifest-legacy-version": "true",
            "table_conf@_metadata.iceberg.hive-client-class": "com.amazonaws.glue.catalog.metastore.AWSCatalogMetastoreClient",
            "table_conf@_fs.s3.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem",
            "table_conf@_fs.s3.buffer.dir": "/var/tmp",
            "table_conf@_sink.parallelism": "4"
          }
        }
      ]
    }
  },
  "FlinkApplicationConfiguration": {
    "ParallelismConfiguration": {
      "AutoScalingEnabled": true,
      "Parallelism": 4,
      "ParallelismPerKPU": 1
    }
  },
  "CloudWatchLoggingOptions": [
    {
      "LogStreamARN": "arn:aws:logs:us-west-2:YourAccountId:log-group:/aws/kinesis-analytics/kafka-cdc-paimon:log-stream:kinesis-analytics-log-stream"
    }
  ]
}
```

Last, create an MSF application using AWS CLI.

```shell
aws kinesisanalyticsv2 create-application \
--cli-input-json file://create-kafkacdc-paimon.json
```

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

### Generating data

You can use [Kinesis Data Generator](https://github.com/awslabs/amazon-kinesis-data-generator),
also available in a [hosted version](https://awslabs.github.io/amazon-kinesis-data-generator/web/producer.html),
to generate random data to Kinesis Data Stream and test the application.