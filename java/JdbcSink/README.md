## Flink JDBC Sink 

This example demonstrates how to use the DataStream API JdbcSink to write to a relational database.

* Flink version: 1.20
* Flink API: DataStream
* Language: Java (11)
* Flink connectors: JDBC sink, DataGen

This example demonstrates how to do UPSERT into a relational database.
The example uses the UPSERT syntax of PostgreSQL, but it can be easily adapted to the syntaxes of other databases or into
an append-only sink, with an INSERT INTO statement.

#### Which JdbcSink? 

At the moment of publishing this example (August 2025) there are two different DataStream API JdbcSink implementations, 
available with the version `3.3.0-1.20` of the JDBC connector.

1. The new `org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink` which uses the Sink API V2 and 
   is initialized using a builder: `JdbcSink.<StockPrice>builder()..build()`
2. The legacy `org.apache.flink.connector.jdbc.JdbcSink` which uses the legacy `SinkFunction` API, now deprecated.
   The legacy sink is initialized with the syntax `JdbcSink.sink(...)`

This example uses the new sink.

At the moment of publishing this example (August 2025) the Apache Flink documentation 
[still refers to the deprecated sink](https://nightlies.apache.org/flink/flink-docs-lts/docs/connectors/datastream/jdbc/#jdbcsinksink).

### Data

The application generates comprehensive `StockPrice` objects with realistic fake data:

```json
{
  "symbol": "AAPL",
  "timestamp": "2025-08-07T10:30:45",
  "price": 150.25
}
```

This data is written using upsert in the following database table, containing the latest price for every symbol.

The sink uses the PostgreSQL upsert syntax:

```
INSERT INTO prices (symbol, price, timestamp) VALUES (?, ?, ?)
  ON CONFLICT(symbol) DO UPDATE SET price = ?, timestamp = ?
```

This is specific to PostgreSQL, but the code can be adjusted to other databases as long as the SQL syntax supports doing
an upsert with a single SQL statement.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID   | Key                  | Description                                                                                                                   | 
|------------|----------------------|-------------------------------------------------------------------------------------------------------------------------------| 
| `DataGen`  | `records.per.second` | Number of stock price records to generate per second (default: 10)                                                            |
| `JdbcSink` | `url`                | PostgreSQL JDBC URL. e.g. `jdbc:postgresql://your-rds-endpoint:5432/your-database`. Note: the URL includes the database name. |
| `JdbcSink` | `table.name`         | Destination table. e.g. `prices` (default: "prices")                                                                          |
| `JdbcSink` | `username`           | Database user with INSERT and UPDATE permissions                                                                              |
| `JdbcSink` | `password`           | Database password                                                                                                             |
| `JdbcSink` | `batch.size`         | Number of records to batch before executing the SQL statement (default: 100)                                                  |
| `JdbcSink` | `batch.interval.ms`  | Maximum time in milliseconds to wait before executing a batch (default: 200)                                                  |
| `JdbcSink` | `max.retries`        | Maximum number of retries for failed database operations (default: 5)                                                         |


### Database prerequisites

When running on Amazon Managed Service for Apache Flink with databases on AWS, you need to set up the database manually, 
ensuring you set up all the following:

> You can find the SQL script that sets up the dockerized database by checking out the init script for 
> [PostgreSQL](docker/postgres-init/init.sql).

1. **PostgreSQL Database**
   1. The database name must match the `url` configured in the JDBC sink
   2. The destination table must have the following schema:
      ```sql
      CREATE TABLE prices (
          symbol VARCHAR(10) PRIMARY KEY,
          timestamp TIMESTAMP NOT NULL,
          price DECIMAL(10,2) NOT NULL
      );
      ```
   3. The database user must have SELECT, INSERT, and UPDATE permissions on the prices table


### Testing with local database using Docker Compose

This example can be run locally using Docker.

A [Docker Compose file](./docker/docker-compose.yml) is provided to run a local PostgreSQL database.
The local database is initialized by creating the database, user, and prices table with sample data.

You can run the Flink application inside your IDE following the instructions in [Running in IntelliJ](#running-in-intellij).

To start the local database run `docker compose up -d` in the `./docker` folder.

Use `docker compose down -v` to shut it down, also removing the data volumes.


### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.
Run the database locally using Docker Compose, as described [above](#testing-with-local-database-using-docker-compose).

See [Running examples locally](../running-examples-locally.md) for details about running the application in the IDE.


### Running on Amazon Managed Service for Apache Flink

To run the application in Amazon Managed Service for Apache Flink ensure the application configuration has the following:
* VPC networking
* The selected Subnets can route traffic to the PostgreSQL database
* The Security Group allows traffic from the application to the database


### Security Considerations

For production deployments:
1. Store database credentials in AWS Secrets Manager. The [Fetch Secrets](../FetchSecrets) example shows how you can fetch
   a secret on application start.
2. Use VPC endpoints for secure database connectivity.
3. Enable SSL/TLS for database connections.

> ⚠️ **Password rotation**: if the password of your database is rotated, the JdbcSink fails, causing the job to restart. 
> If you fetch the password dynamically on application start (when you create the JdbcSink object) the job will be able
> to restart with the new password. Fetching the password on start is not shown in this example.

### Implementation considerations

#### At-least-once or exactly-once

This implementation leverages the at-least-once mode of the JdbcSink. This is normally sufficient when the sink is 
executing a single idempotent statement such as an UPSERT: any duplicate will just overwrite the same record.

The JdbcSink also supports exactly-once mode which leverages XA transactions synchronized with Flink checkpoints, 
and relies on XADataSource. This prevents duplicate writes in case of failure and restart from checkpoint. Note that it 
does not prevent duplicates if you restart the application from an older Snapshot (Flink Savepoint), unless your SQL statement
implements some form of idempotency.

#### No connection pooler?

The JdbcSink does not support using any database connection pooler, such as HikariCP. 

The reason is that no connection pooling is required. The sink will open one database connection per parallelism (one per subtask),
and reuse these connections unless they get closed.

#### Batching

The JdbcSink batches writes to reduce the number of requests to the database.
The batch size and interval used in this example are for demonstrational purposes only.

You should test your actual application with a realistic throughput and realistic data to optimize these values for your 
workload.


#### Which flink-connector-jdbc-* dependency?

To use JdbcSink in DataStream API, you need `flink-connector-jdbc-core` and the JDBC driver of the specific database. For example:
```
<dependency>
   <groupId>org.apache.flink</groupId>
   <artifactId>flink-connector-jdbc-core</artifactId>
   <version>3.3.0-1.20</version>
</dependency>

<dependency>
   <groupId>org.postgresql</groupId>
   <artifactId>postgresql</artifactId>
   <version>42.7.2</version>
</dependency>
```

Including `flink-connector-jdbc` would bring in unnecessary dependencies and increase the size of the uber-jar file.
