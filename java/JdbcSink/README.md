## Flink JDBC Sink 

This example demonstrates how to use Apache Flink's DataStream API JDBC Sink to execute UPSERT into a relational database.
The example leverages the UPSERT functionality of PostgreSQL.

* Flink version: 1.20
* Flink API: DataStream
* Language: Java (11)
* Flink connectors: JDBC sink, DataGen

### Data

The application generates comprehensive `StockPrice` objects with realistic fake data:

```json
{
  "symbol": "AAPL",
  "timestamp": "2024-07-25T10:30:45",
  "price": 150.25
}
```

This data are written doing upsert in the following database table, containing the latest price for every symbol.

```sql
CREATE TABLE prices (
    symbol VARCHAR(10) PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    price DECIMAL(10,2) NOT NULL
);
```

The sink uses the PostgreSQL upsert syntax:

```
INSERT INTO prices (symbol, price, timestamp) VALUES (?, ?, ?)
  ON CONFLICT(symbol) DO UPDATE SET price = ?, timestamp = ?
```

This is specific to PostgreSQL, but the code can be adjusted to other databases as long as the SQL syntax support doing
an upsert with a single SQL statement.

### Database prerequisites

When running on Amazon Managed Service for Apache Flink and with databases on AWS, you need to set up the database manually, 
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


## Testing with local database using Docker Compose

This example can be run locally using Docker.

A [Docker Compose file](./docker/docker-compose.yml) is provided to run a local PostgreSQL database.
The local database is initialized by creating the database, user, and prices table with sample data.

You can run the Flink application inside your IDE following the instructions in [Running in IntelliJ](#running-in-intellij).

To start the local database run `docker compose up -d` in the `./docker` folder.

Use `docker compose down -v` to shut it down, also removing the data volumes.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID   | Key                | Description                                                                                                                | 
|------------|--------------------|----------------------------------------------------------------------------------------------------------------------------| 
| `DataGen`  | `records.per.second` | Number of stock price records to generate per second (default: 10)                                                        |
| `JdbcSink` | `url`              | PostgreSQL JDBC URL. e.g. `jdbc:postgresql://your-rds-endpoint:5432/your-database`. Note: the URL includes the database name. |
| `JdbcSink` | `table.name`       | Destination table. e.g. `prices` (default: "prices")                                                                      |
| `JdbcSink` | `username`         | Database user with INSERT and UPDATE permissions                                                                           |
| `JdbcSink` | `password`         | Database password                                                                                                          |


### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.
Run the database locally using Docker Compose, as described [above](#testing-with-local-database-using-docker-compose).

See [Running examples locally](../running-examples-locally.md) for details about running the application in the IDE.


### Running on Amazon Managed Service for Apache Flink

To run the application in Amazon Managed Service for Apache Flink make sure the application configuration has the following:
* VPC networking
* The selected Subnets can route traffic to the PostgreSQL database
* The Security Group allows traffic from the application to the database


### Security Considerations

For production deployments:
1. Store database credentials in AWS Secrets Manager
2. Use VPC endpoints for secure database connectivity
3. Enable SSL/TLS for database connections
4. Configure appropriate IAM roles and policies
5. Use RDS with encryption at rest and in transit
