package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGeneratorFunction;
import com.amazonaws.services.msf.jdbc.StockPricePostgresUpsertQueryStatement;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.jdbc.datasource.connections.SimpleJdbcConnectionProvider;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
     * Create the JDBC Sink
     */
    private static JdbcSink<StockPrice> createUpsertJdbcSink(Properties sinkProperties) {
        Preconditions.checkNotNull(sinkProperties, "JdbcSink configuration group missing");

        // This example is designed for PostgreSQL. Switching to a different RDBMS requires modifying the JdbcQueryStatement
        // implementation which depends on the upsert syntax of the specific RDBMS.
        String jdbcDriver = "org.postgresql.Driver";


        String jdbcUrl = Preconditions.checkNotNull(
                sinkProperties.getProperty("url"),
                "JDBC URL is required"
        );
        String dbUser = Preconditions.checkNotNull(
                sinkProperties.getProperty("username"),
                "JDBC username is required"
        );
        // In the real application the password should have been encrypted or fetched at runtime
        String dbPassword = Preconditions.checkNotNull(
                sinkProperties.getProperty("password"),
                "JDBC password is required"
        );

        String tableName = sinkProperties.getProperty("table.name", "prices");

        return JdbcSink.<StockPrice>builder()
                // The JdbcQueryStatement implementation provides the SQL statement template and converts the input record
                // into parameters passed to the statement.
                .withQueryStatement(new StockPricePostgresUpsertQueryStatement(tableName))
                .withExecutionOptions(JdbcExecutionOptions.builder()
                        .withBatchSize(100)
                        .withBatchIntervalMs(200L)
                        .withMaxRetries(5)
                        .build())
                // Using a simple connection provider which does not reuse connection
                .buildAtLeastOnce(new SimpleJdbcConnectionProvider(new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(jdbcUrl)
                        .withDriverName(jdbcDriver)
                        .withUsername(dbUser)
                        .withPassword(dbPassword)
                        .build())
                );
    }


    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        // Load application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        // Create a DataGeneratorSource that generates StockPrice objects
        Properties dataGenProperties = applicationProperties.get("DataGen");
        DataGeneratorSource<StockPrice> source = createDataGeneratorSource(
                dataGenProperties,
                new StockPriceGeneratorFunction(),
                TypeInformation.of(StockPrice.class)
        );

        // Create the data stream from the source
        DataStream<StockPrice> stockPriceStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Stock Price Data Generator"
        ).uid("stock-price-data-generator");

        // Create the JDBC sink
        Properties sinkProperties = applicationProperties.get("JdbcSink");
        JdbcSink<StockPrice> jdbcSink = createUpsertJdbcSink(sinkProperties);

        // Attach the sink
        stockPriceStream.sinkTo(jdbcSink).uid("jdbc-sink").name("PostgreSQL Sink");

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
