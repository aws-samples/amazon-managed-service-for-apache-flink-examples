# Flink JDBC Sink Example with JavaFaker

This example demonstrates how to use Apache Flink's DataStream API to generate realistic fake user data using JavaFaker and write it to a PostgreSQL database using the JDBC connector.

## Overview

The application:
1. Generates realistic fake user data using JavaFaker library
2. Uses the DataGen connector for controlled data generation rates
3. Writes the data to a PostgreSQL database using the JDBC connector
4. Supports configurable data generation rates
5. Includes proper error handling and retry mechanisms

## Architecture

```
DataGeneratorSource -> JavaFaker -> DataStream<User> -> JDBC Sink -> PostgreSQL
```

## Generated Data Schema

The application generates comprehensive `User` objects with realistic fake data:

```json
{
  "user_id": 1,
  "first_name": "John",
  "last_name": "Smith",
  "email": "john.smith@example.com",
  "phone_number": "+1-555-123-4567",
  "address": "123 Main Street",
  "city": "New York",
  "country": "United States",
  "job_title": "Software Engineer",
  "company": "Tech Corp",
  "date_of_birth": "1990-05-15",
  "created_at": "2024-07-15T10:30:45"
}
```

## JavaFaker Integration

The application uses [JavaFaker](https://github.com/DiUS/java-faker) to generate realistic fake data:

- **Personal Information**: Names, emails, phone numbers, addresses
- **Location Data**: Cities, countries with realistic combinations
- **Professional Data**: Job titles, company names
- **Demographics**: Date of birth (ensuring users are 18+ years old)
- **Timestamps**: ISO formatted creation timestamps

## Database Schema

The PostgreSQL table structure accommodates comprehensive user information:

```sql
CREATE TABLE users (
    user_id INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    address VARCHAR(200),
    city VARCHAR(50),
    country VARCHAR(50),
    job_title VARCHAR(100),
    company VARCHAR(100),
    date_of_birth DATE,
    created_at TIMESTAMP NOT NULL
);
```

### Indexes

The table includes optimized indexes for common query patterns:
- Email (unique constraint + index)
- Creation timestamp
- Name combinations
- Location (city, country)
- Company and job title

## Configuration

The application uses two property groups:

### DataGen Properties
- `records.per.second`: Number of records to generate per second (default: 10)

### JdbcSink Properties
- `url`: JDBC connection URL
- `username`: Database username
- `password`: Database password
- `table.name`: Target table name (default: "users")

## Local Development

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher
- Docker and Docker Compose

### Running Locally

1. **Start PostgreSQL database:**
   ```bash
   cd docker
   docker-compose up -d
   ```

2. **Verify database is running:**
   ```bash
   docker-compose logs postgres
   ```

3. **Connect to database (optional):**
   ```bash
   docker exec -it postgres_jdbc_sink psql -U flinkuser -d testdb
   ```

4. **Build the application:**
   ```bash
   mvn clean package
   ```

5. **Run the application:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.amazonaws.services.msf.JdbcSinkJob"
   ```

### Quick Start

Use the automated setup script:
```bash
./test-local.sh
```

### Configuration for Local Development

The local configuration is defined in `src/main/resources/flink-application-properties-dev.json`:

```json
[
  {
    "PropertyGroupId": "DataGen",
    "PropertyMap": {
      "records.per.second": "5"
    }
  },
  {
    "PropertyGroupId": "JdbcSink",
    "PropertyMap": {
      "url": "jdbc:postgresql://localhost:5432/testdb",
      "username": "flinkuser",
      "password": "flinkpassword",
      "table.name": "users"
    }
  }
]
```

### Monitoring

Use the enhanced monitoring dashboard:
```bash
./monitor.sh
```

The monitoring script provides insights into:
- **Record counts and generation rates**
- **Geographic distribution** (countries, cities)
- **Professional data** (companies, job titles)
- **Demographics** (age distribution)
- **Data quality metrics**
- **Email domain analysis**

Sample monitoring output:
```sql
-- View recent realistic records
SELECT user_id, first_name, last_name, city, country, job_title, company 
FROM users ORDER BY created_at DESC LIMIT 5;

-- Analyze geographic distribution
SELECT country, COUNT(*) as count 
FROM users GROUP BY country ORDER BY count DESC LIMIT 10;

-- Check age distribution
SELECT 
    CASE 
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 18 AND 25 THEN '18-25'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 26 AND 35 THEN '26-35'
        -- ... more age groups
    END as age_group,
    COUNT(*) as count
FROM users GROUP BY age_group;
```

## Deployment to Amazon Managed Service for Apache Flink

### Application Properties

Configure the following application properties in your Managed Flink application:

```json
[
  {
    "PropertyGroupId": "DataGen",
    "PropertyMap": {
      "records.per.second": "100"
    }
  },
  {
    "PropertyGroupId": "JdbcSink",
    "PropertyMap": {
      "url": "jdbc:postgresql://your-rds-endpoint:5432/your-database",
      "username": "your-username",
      "password": "your-password",
      "table.name": "users"
    }
  }
]
```

### Security Considerations

For production deployments:
1. Store database credentials in AWS Secrets Manager
2. Use VPC endpoints for secure database connectivity
3. Enable SSL/TLS for database connections
4. Configure appropriate IAM roles and policies
5. Consider data privacy implications of generated fake data

### Performance Tuning

The JDBC sink is configured with:
- Batch size: 1000 records
- Batch interval: 200ms
- Max retries: 5

JavaFaker performance considerations:
- Faker instances are thread-safe and reusable
- Data generation is deterministic with consistent quality
- Memory usage is minimal for the faker instance

**Note on JDBC API**: This example currently uses the `JdbcSink.sink()` method which may be deprecated in favor of `org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink`. However, the new API is not yet available in the current JDBC connector version (3.3.0-1.20). The implementation will be updated to use the newer non-deprecated API once it becomes available in stable form.

## Data Quality and Realism

JavaFaker provides:
- **Realistic names** from various cultures and backgrounds
- **Valid email formats** with diverse domains
- **Proper phone number formats** for different regions
- **Real city and country combinations**
- **Believable job titles and company names**
- **Consistent age ranges** (18+ years old)

## Troubleshooting

### Common Issues

1. **Connection refused to PostgreSQL:**
   - Ensure Docker container is running: `docker-compose ps`
   - Check port availability: `netstat -an | grep 5432`

2. **Table does not exist:**
   - Verify initialization script ran: `docker-compose logs postgres`
   - Check if new schema was applied correctly

3. **JavaFaker dependency issues:**
   - Ensure Maven downloaded dependencies: `mvn dependency:resolve`
   - Check for version conflicts: `mvn dependency:tree`

4. **Data generation performance:**
   - JavaFaker is optimized for realistic data generation
   - Consider adjusting `records.per.second` for your use case

### Logs

Check application logs for:
- JDBC connection status
- Batch execution metrics
- JavaFaker data generation performance
- Error messages and stack traces

## Dependencies

Key dependencies used in this example:
- `flink-connector-datagen`: For controlled data generation
- `flink-connector-jdbc`: For JDBC database connectivity
- `postgresql`: PostgreSQL JDBC driver
- `javafaker`: For realistic fake data generation
- `aws-kinesisanalytics-runtime`: For reading application properties

## Related Examples

- **FlinkDataGenerator**: Shows basic DataGen connector usage
- **FlinkCDC/FlinkCDCSQLServerSource**: Demonstrates CDC source with JDBC sink using Table API

## License

This example is provided under the same license as the parent repository.
