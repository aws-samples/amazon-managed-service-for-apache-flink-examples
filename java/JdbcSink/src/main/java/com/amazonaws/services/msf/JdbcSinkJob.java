package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGeneratorFunction;
import com.axiomalaska.jdbc.NamedParameterPreparedStatement;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

/**
 * A Flink application that generates random stock price data using DataGeneratorSource
 * and writes it to a PostgreSQL database using the JDBC connector.
 */
public class JdbcSinkJob {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcSinkJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Default values for configuration
    private static final int DEFAULT_RECORDS_PER_SECOND = 10;

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    JdbcSinkJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    /**
     * Create a DataGeneratorSource with configurable rate from DataGen properties
     *
     * @param dataGenProperties Properties from the "DataGen" property group
     * @param generatorFunction The generator function to use for data generation
     * @param typeInformation   Type information for the generated data type
     * @param <T>               The type of data to generate
     * @return Configured DataGeneratorSource
     */
    private static <T> DataGeneratorSource<T> createDataGeneratorSource(
            Properties dataGenProperties,
            GeneratorFunction<Long, T> generatorFunction,
            TypeInformation<T> typeInformation) {

        int recordsPerSecond;
        if (dataGenProperties != null) {
            String recordsPerSecondStr = dataGenProperties.getProperty("records.per.second");
            if (recordsPerSecondStr != null && !recordsPerSecondStr.trim().isEmpty()) {
                try {
                    recordsPerSecond = Integer.parseInt(recordsPerSecondStr.trim());
                } catch (NumberFormatException e) {
                    LOG.error("Invalid records.per.second value: '{}'. Must be a valid integer. ", recordsPerSecondStr);
                    throw e;
                }
            } else {
                LOG.info("No records.per.second configured. Using default: {}", DEFAULT_RECORDS_PER_SECOND);
                recordsPerSecond = DEFAULT_RECORDS_PER_SECOND;
            }
        } else {
            LOG.info("No DataGen properties found. Using default records per second: {}", DEFAULT_RECORDS_PER_SECOND);
            recordsPerSecond = DEFAULT_RECORDS_PER_SECOND;
        }

        Preconditions.checkArgument(recordsPerSecond > 0,
                "Invalid records.per.second value. Must be positive.");

        return new DataGeneratorSource<T>(
                generatorFunction,
                Long.MAX_VALUE, // Generate (practically) unlimited records
                RateLimiterStrategy.perSecond(recordsPerSecond), // Configurable rate
                typeInformation // Explicit type information
        );
    }



    /**
     * Create a JDBC Sink for PostgreSQL using NamedParameterPreparedStatement from axiomalaska library
     *
     * @param jdbcProperties Properties from the "JdbcSink" property group
     * @return an instance of SinkFunction for StockPrice objects
     */
    private static SinkFunction<StockPrice> createJdbcSink(Properties jdbcProperties) {
        String jdbcUrl = Preconditions.checkNotNull(
                jdbcProperties.getProperty("url"),
                "JDBC URL is required"
        );
        String username = Preconditions.checkNotNull(
                jdbcProperties.getProperty("username"),
                "JDBC username is required"
        );
        String password = Preconditions.checkNotNull(
                jdbcProperties.getProperty("password"),
                "JDBC password is required"
        );
        String tableName = jdbcProperties.getProperty("table.name", "prices");

        // SQL statement leveraging PostgreSQL UPSERT syntax
        String namedSQL = String.format(
                "INSERT INTO %s (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                        "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp",
                tableName
        );

        LOG.info("Named SQL: {}", namedSQL);

        // JDBC connection options
        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl(jdbcUrl)
                .withDriverName("org.postgresql.Driver")
                .withUsername(username)
                .withPassword(password)
                .build();

        // JDBC execution options
        JdbcExecutionOptions executionOptions = JdbcExecutionOptions.builder()
                .withBatchSize(1000)
                .withBatchIntervalMs(200)
                .withMaxRetries(5)
                .build();

        // JDBC statement builder using NamedParameterPreparedStatement from axiomalaska
        JdbcStatementBuilder<StockPrice> statementBuilder = new JdbcStatementBuilder<StockPrice>() {
            @Override
            public void accept(PreparedStatement preparedStatement, StockPrice stockPrice) throws SQLException {
                // Get the connection from the PreparedStatement
                Connection connection = preparedStatement.getConnection();
                
                // Create NamedParameterPreparedStatement using the axiomalaska library
                // This library creates its own PreparedStatement internally from the connection and named SQL
                try (NamedParameterPreparedStatement namedStmt = NamedParameterPreparedStatement.createNamedParameterPreparedStatement(connection, namedSQL)) {
                    
                    // Set parameters by name using the axiomalaska library
                    namedStmt.setString("symbol", stockPrice.getSymbol());
                    
                    // Parse the ISO timestamp and convert to SQL Timestamp
                    LocalDateTime dateTime = LocalDateTime.parse(stockPrice.getTimestamp(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    namedStmt.setTimestamp("timestamp", Timestamp.valueOf(dateTime));
                    
                    namedStmt.setBigDecimal("price", stockPrice.getPrice());
                    
                    // Execute the statement
                    namedStmt.executeUpdate();
                }
            }
        };

        // We need to provide a dummy SQL for Flink's JDBC sink since we're handling execution ourselves
        // The actual SQL execution is done by NamedParameterPreparedStatement in the statement builder
        String dummySQL = "SELECT 1";
        
        // Use the deprecated but working JdbcSink.sink() method
        return JdbcSink.sink(dummySQL, statementBuilder, executionOptions, connectionOptions);
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        // Load application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        // Create a DataGeneratorSource that generates StockPrice objects
        DataGeneratorSource<StockPrice> source = createDataGeneratorSource(
                applicationProperties.get("DataGen"),
                new StockPriceGeneratorFunction(),
                TypeInformation.of(StockPrice.class)
        );

        // Create the data stream from the source
        DataStream<StockPrice> stockPriceStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Stock Price Data Generator"
        ).uid("stock-price-data-generator");

        // Check if JDBC sink is configured
        Properties jdbcProperties = applicationProperties.get("JdbcSink");
        if (jdbcProperties == null) {
            throw new IllegalArgumentException(
                    "JdbcSink configuration is required. Please provide 'JdbcSink' configuration group.");
        }

        // Create JDBC sink
        SinkFunction<StockPrice> jdbcSink = createJdbcSink(jdbcProperties);
        stockPriceStream.addSink(jdbcSink).uid("jdbc-sink").name("PostgreSQL Sink");

        // Add print sink for local testing
        if (isLocal(env)) {
            stockPriceStream.print().uid("print-sink").name("Print Sink");
            LOG.info("Print sink configured for local testing");
        }

        LOG.info("JDBC sink configured");

        // Execute the job
        env.execute("Flink JDBC Sink Job - Stock Prices");
    }
}
