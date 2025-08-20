# FlinkCDC SQL Server source example

This example shows how to capture data from a database (SQL Server in this case) directly from Flink using a Flink CDC source connector.

* Flink version: 1.20
* Flink API: SQL
* Language: Java (11)
* Flink connectors: Flink CDC SQL Server source (3.4), JDBC sink

The job is implemented in SQL embedded in Java. 
It uses the [Flink CDC SQL Server source connector](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc/)
to capture changes from a database.
Changes are propagated to the destination database using [JDBC Sink connector](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/jdbc/).

### Source database

This example uses Ms SQL Server as source database. To use a different database as CDC source you need to switch to a different Flink CDC Source connector.

Different Flink CDC Sources require different configurations and support different metadata fields. To switch the source to a different database you need to modify the code.

See [Flink CDC Sources documentation](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc) for further details.


### Destination database

Note that the JDBC sink is agnostic to the actual destination database technology. 
This example is tested with both MySQL and PostgreSQL but can be easily adjusted to different databases.

The `url` property in the `JdbcSink` configuration group decides the destination database (see [Runtime configuration](#runtime-configuration), below).
The correct JDBC driver must be included in the `pom.xml`. This example includes both MySQL and PostgreSQL drivers.

### Testing with local databases using Docker Compose

This example can be run locally using Docker.

A [Docker Compose file](./docker/docker-compose.yml) is provided to run local SQL Server, MySQL and PostgreSQL databases.
The local databases are initialized by creating users, databases and tables. Some initial records are also inserted into the source table.

You can run the Flink application inside your IDE following the instructions in [Running in IntelliJ](#running-in-intellij). 
The default local configuration connects to the local PostgreSQL db defined in Docker Compose.

To start the local databases run `docker compose up -d` in the `./docker` folder.

Use `docker compose down -v` to shut them down, also removing the data volumes. 


### Database prerequisites

When running on Amazon Managed Service for Apache Flink and with databases on AWS, you need to set up the databases manually, ensuring you set up all the following:

> YYou can find the SQL scripts that set up the dockerized databases by checking out the init scripts for 
> [SQL Server](docker/sqlserver-init/init.sql), [MySQL](docker/mysql-init/init.sql), 
> and [PostgreSQL](docker/postgres-init/init.sql).

1. **Source database (Ms SQL Server)**
   1. SQL Server Agent must be running
   2. Native (user/password) authentication must be enabled
   3. The login used by Flink CDC (e.g. `flink_cdc`) must be `db_owner` for the database
   4. The source database and table must match the `database.name` and `table.name` you specify in the source configuration (e.g. `SampleDataPlatform` and `Customers`)
   5. The source table must have this schema:
      ```sql
      CREATE TABLE [dbo].[Customers]
      (
         [CustomerID]    [int] IDENTITY (1,1) NOT NULL,
         [FirstName]     [nvarchar](40)       NOT NULL,
         [MiddleInitial] [nvarchar](40)       NULL,
         [LastName]      [nvarchar](40)       NOT NULL,
         [mail]          [varchar](50)        NULL,
      CONSTRAINT [CustomerPK] PRIMARY KEY CLUSTERED ([CustomerID] ASC)
      ) ON [PRIMARY];
      ```
   6. CDC must be enabled both on the source database. On Amazon RDS SQL Server use the following stored procedure:
      ```sql
      exec msdb.dbo.rds_cdc_enable_db 'MyDB'
      ```
      On self-managed SQL server you need to call a different procedure, while in the database:
       ```sql
       USE MyDB;
       EXEC sys.sp_cdc_enable_db;
       ```
   7. CDC must also be enabled on the table:   
       ```sql
       EXEC sys.sp_cdc_enable_table
            @source_schema = N'dbo',
            @source_name = N'Customers',
            @role_name = NULL,
            @supports_net_changes = 0;
       ```
2. **Destination database (MySQL or PostgreSQL)**
   1. The destination database name must match the `url` configured in the JDBC sink
   2. The destination table must have the following schema
         ```sql
         CREATE TABLE customers (
           customer_id INT PRIMARY KEY,
           first_name VARCHAR(40),
           middle_initial VARCHAR(40),
           last_name VARCHAR(40),
           email VARCHAR(50),
           _source_updated_at TIMESTAMP,
           _change_processed_at TIMESTAMP
         );
         ```
   3. The destination database user must have SELECT, INSERT, UPDATE and DELETE permissions on the destination table 

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.
Run the databases locally using Docker Compose, as described [above](#testing-with-local-databases-using-docker-compose).

See [Running examples locally](../../running-examples-locally.md) for details about running the application in the IDE.


### Running on Amazon Managed Service for Apache Flink

To run the application in Amazon Managed Service for Apache Flink make sure the application configuration has the following:
* VPC networking 
* The selected Subnets can route traffic to both the source and destination databases
* The Security Group allows traffic from the application to both source and destination databases


### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID    | Key             | Description                                                                                                                | 
|-------------|-----------------|----------------------------------------------------------------------------------------------------------------------------|
| `CDCSource` | `hostname`      | Source database DNS hostname or IP                                                                                         |
| `CDCSource` | `port`          | Source database port (normally `1433`)                                                                                     |
| `CDCSource` | `username`      | Source database username. The user must be `dbo_owner` of the database                                                     |
| `CDCSource` | `password`      | Source database password                                                                                                   |
| `CDCSource` | `database.name` | Source database name                                                                                                       |
| `CDCSource` | `table.name`    | Source table name. e.g. `dbo.Customers`                                                                                    |
| `JdbcSink`  | `url`           | Destination database JDBC URL. e.g. `jdbc:postgresql://localhost:5432/targetdb`. Note: the URL includes the database name. |
| `JdbcSink`  | `table.name`    | Destination table. e.g. `customers`                                                                                        |
| `JdbcSink`  | `username`      | Destination database user                                                                                                  |
| `JdbcSink`  | `password`      | Destination database password                                                                                              |


#### Passing credentials

For simplicity, this example passes the credentials of the database as runtime parameters.
This is **not best practice** in production workload.

We recommend to fetch credentials on application start, for example from AWS Secrets Manager, as demonstrated in the 
[Fetch Secrets](../../FetchSecrets) example.

### Known limitations

Using the SQL interface of Flink CDC Sources greatly simplifies the implementation of a passthrough application.
However, schema changes in the source table are ignored.

## References

* [Flink CDC SQL Server documentation](https://nightlies.apache.org/flink/flink-cdc-docs-release-3.4/docs/connectors/flink-sources/sqlserver-cdc)
* [Debezium SQL Server documentation](https://debezium.io/documentation/reference/1.9/connectors/sqlserver.html)