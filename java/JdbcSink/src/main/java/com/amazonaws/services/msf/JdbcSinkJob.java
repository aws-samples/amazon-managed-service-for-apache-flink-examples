package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGeneratorFunction;
import com.amazonaws.services.msf.jdbc.StockPriceUpsertQueryStatement;
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

import static com.amazonaws.services.msf.ConfigurationHelper.*;

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
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long DEFAULT_BATCH_INTERVAL_MS = 200L;
    private static final int DEFAULT_MAX_RETRIES = 5;

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

        Preconditions.checkNotNull(dataGenProperties, "DataGen configuration group missing");

        int recordsPerSecond = extractIntParameter(dataGenProperties, "records.per.second", DEFAULT_RECORDS_PER_SECOND);


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

        // This example is designed for PostgreSQL. Switching to a different RDBMS requires modifying
        // StockPriceUpsertQueryStatement implementation which depends on the upsert syntax of the specific RDBMS.
        String jdbcDriver = "org.postgresql.Driver";

        String jdbcUrl = extractRequiredStringParameter(sinkProperties, "url", "JDBC URL is required");
        String dbUser = extractRequiredStringParameter(sinkProperties, "username", "JDBC username is required");
        // In the real application the password should have been encrypted or fetched at runtime
        String dbPassword = extractRequiredStringParameter(sinkProperties, "password", "JDBC password is required");

        String tableName = extractStringParameter(sinkProperties, "table.name", "prices");

        int batchSize = extractIntParameter(sinkProperties, "batch.size", DEFAULT_BATCH_SIZE);
        long batchIntervalMs = extractLongParameter(sinkProperties, "batch.interval.ms", DEFAULT_BATCH_INTERVAL_MS);
        int maxRetries = extractIntParameter(sinkProperties, "max.retries", DEFAULT_MAX_RETRIES);

        LOG.info("JDBC Sink configuration - batchSize: {}, batchIntervalMs: {}, maxRetries: {}",
                batchSize, batchIntervalMs, maxRetries);

        return JdbcSink.<StockPrice>builder()
                // The JdbcQueryStatement implementation provides the SQL statement template and converts the input record
                // into parameters passed to the statement.
                .withQueryStatement(new StockPriceUpsertQueryStatement(tableName))
                .withExecutionOptions(JdbcExecutionOptions.builder()
                        .withBatchSize(batchSize)
                        .withBatchIntervalMs(batchIntervalMs)
                        .withMaxRetries(maxRetries)
                        .build())
                // The SimpleJdbcConnectionProvider is good enough in this case. The connector will open one db connection per parallelism
                // and reuse the same connection on every write. There is no need of a connection pooler
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
