# FlinkCDC SQL Server source example

This example shows how to capture data from a database (SQL Server in this case) directly from Flink using a Flink CDC source connector.

Flink version: 1.20
Flink API: SQL
Language: Java (11)
Flink connectors: Flink CDC SQL Server source (3.4), DynamoDB sink

The job is implemented in SQL embedded in Java. It uses the [Flink CDC SQL Server source connector](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc/)
to capture changes from a database, and sink data to a DynamoDB table doing upsert by primary key, so the destination table always contains the latest state of each record.

> ⚠️ DynamoDB SQL sink does not currently support DELETE (see [FLINK-35500](https://issues.apache.org/jira/browse/FLINK-35500))

TBD

## References

* [Flink CDC SQL Server documentation](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc)
* [Debezium SQL Server documentation](https://debezium.io/documentation/reference/1.9/connectors/sqlserver.html)