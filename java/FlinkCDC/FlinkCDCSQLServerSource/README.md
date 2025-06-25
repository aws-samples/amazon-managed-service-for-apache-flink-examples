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

### Requirements

1. SQL Server Agent must be running
2. The user passed to FlinCDC must be `db_owner` for the database
3. CDC must be enabled both on the database AND on the table
    ```sql
    USE MyDB;
    EXEC sys.sp_cdc_enable_db;
    
    EXEC sys.sp_cdc_enable_table
         @source_schema = N'dbo',
         @source_name = N'Customers',
         @role_name = NULL,
         @supports_net_changes = 0;
    ```


## References

* [Flink CDC SQL Server documentation](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc)
* [Debezium SQL Server documentation](https://debezium.io/documentation/reference/1.9/connectors/sqlserver.html)