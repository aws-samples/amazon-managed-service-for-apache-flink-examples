# Stock Price Generator to Kafka

This Flink application generates random stock price data and writes it to a Kafka topic as JSON messages.

## Features

- Generates realistic stock price data for popular symbols (AAPL, GOOGL, MSFT, etc.)
- Configurable data generation rate
- Writes JSON-formatted messages to Kafka
- Uses stock symbol as Kafka message key for partitioning
- Supports both local development and Amazon Managed Service for Apache Flink

## Building

```bash
mvn clean package
```

## Running Locally

1. Start a local Kafka cluster
2. Create the target topic:
   ```bash
   kafka-topics --create --topic stock-prices --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
   ```
3. Run the application:
   ```bash
   mvn exec:java -Dexec.mainClass="com.amazonaws.services.msf.FetchSecretsJob"
   ```

## Configuration

The application uses the following property groups:

- **DataGen**: Controls data generation rate
  - `records.per.second`: Number of records to generate per second (default: 10)

- **Output0**: Kafka sink configuration
  - `bootstrap.servers`: Kafka bootstrap servers
  - `topic`: Target Kafka topic (default: "stock-prices")

- **AuthProperties**: Authentication properties for MSK (when using IAM auth)

## Sample Output

```json
{
  "symbol": "AAPL",
  "timestamp": "2025-08-20T11:30:00.123Z",
  "price": 175.42
}
```
