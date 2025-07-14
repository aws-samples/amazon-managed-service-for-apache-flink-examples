## Iceberg Sink (Glue Data Catalog) using SQL

* Flink version: 1.20.0
* Flink API: SQL API
* Iceberg 1.9.1
* Language: Java (11)
* Flink connectors: [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/)
  and [Iceberg](https://iceberg.apache.org/docs/latest/flink/) sink

This example demonstrates how to use
[Flink SQL API with Iceberg](https://iceberg.apache.org/docs/latest/flink-writes/) and the Glue Data Catalog.

For simplicity, the application generates synthetic data, random stock prices, internally.
Data is generated as POJO objects, simulating a real source, for example a Kafka Source, that receives records
that can be converted to table format for SQL operations.

### Prerequisites

The application expects the following resources:
* A Glue Data Catalog database in the current AWS region. The database name is configurable (default: "default").
  The application creates the Table, but the Catalog must exist already.
* An S3 bucket to write the Iceberg table.

#### IAM Permissions

The application must have IAM permissions to:
* Show and alter Glue Data Catalog databases, show and create Glue Data Catalog tables.
  See [Glue Data Catalog permissions](https://docs.aws.amazon.com/athena/latest/ug/fine-grained-access-to-glue-resources.html).
* Read and Write from the S3 bucket.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from Runtime Properties.

When running locally, the configuration is read from the
[resources/flink-application-properties-dev.json](./src/main/resources/flink-application-properties-dev.json) file.

Runtime parameters:

| Group ID  | Key                      | Default           | Description                                                                                |
|-----------|--------------------------|-------------------|--------------------------------------------------------------------------------------------|
| `DataGen` | `records.per.sec`        | `10.0`            | Records per second generated.                                                              |
| `Iceberg` | `bucket.prefix`          | (mandatory)       | S3 bucket and path URL prefix, starting with `s3://`. For example `s3://mybucket/iceberg`. |
| `Iceberg` | `catalog.db`             | `default`         | Name of the Glue Data Catalog database.                                                    |
| `Iceberg` | `catalog.table`          | `prices_iceberg`  | Name of the Glue Data Catalog table.                                                       |

### Running locally, in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](https://github.com/nicusX/amazon-managed-service-for-apache-flink-examples/blob/main/java/running-examples-locally.md) for details.

### Checkpoints

Checkpointing must be enabled. Iceberg commits writes on checkpoint.

When running locally, the application enables checkpoints programmatically, every 30 seconds.
When deployed to Managed Service for Apache Flink, checkpointing is controlled by the application configuration.

### Sample Data Schema

The application uses a predefined schema for the stock price data with the following fields:
* `timestamp`: STRING - ISO timestamp of the record
* `symbol`: STRING - Stock symbol (e.g., AAPL, AMZN)
* `price`: FLOAT - Stock price (0-10 range)
* `volumes`: INT - Trade volumes (0-1000000 range)

### Known limitations of the Flink Iceberg sink

At the moment there are current limitations concerning Flink Iceberg integration:
* Doesn't support Iceberg Table with hidden partitioning
* Doesn't support adding columns, removing columns, renaming columns or changing columns.

---

### Known Flink issue: Hadoop library clash

When integrating Flink with Iceberg, there's a common issue affecting most Flink setups

When using Flink SQL's `CREATE CATALOG` statements, Hadoop libraries must be available on the system classpath.
However, standard Flink distributions use shaded dependencies that can create class loading conflicts with Hadoop's
expectations.
Flink default classloading, when running in Application mode, prevents from using some Hadoop classes even if
included in the application uber-jar.

#### Solution

This example shows a simple workaround to prevent the Hadoop class clashing:
1. Include a modified version of the Flink class `org.apache.flink.runtime.util.HadoopUtils`
2. Use Maven Shade Plugin to prevent class conflicts

The modified [`org.apache.flink.runtime.util.HadoopUtils`](src/main/java/org/apache/flink/runtime/util/HadoopUtils.java)
class is included in the source code of this project. You can include it as-is in your project, using the same package name.

The shading is configured in the [`pom.xml`](pom.xml). In your project you can copy the `<relocations>...</relocations>` configuration
into the `maven-shade-plugin` configuration.

```xml
<relocations>
    <relocation>
        <pattern>org.apache.hadoop.conf</pattern>
        <shadedPattern>shaded.org.apache.hadoop.conf</shadedPattern>
    </relocation>
    <relocation>
        <pattern>org.apache.flink.runtime.util.HadoopUtils</pattern>
        <shadedPattern>shadow.org.apache.flink.runtime.util.HadoopUtils</shadedPattern>
    </relocation>
</relocations>
```