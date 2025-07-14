package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.source.StockPriceGeneratorFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class IcebergSQLSinkJob {
    private static final Logger LOG = LoggerFactory.getLogger(IcebergSQLSinkJob.class);

    // Constants
    private static final String CATALOG_NAME = "glue";
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    public static final String DEFAULT_DATABASE = "default";
    public static final String DEFAULT_TABLE = "prices_iceberg";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    private static void validateURI(String uri) {
        String s3UriPattern = "^s3://([a-z0-9.-]+)(/[a-z0-9-_/]+/?)$";
        Preconditions.checkArgument(uri != null && uri.matches(s3UriPattern),
                "Invalid S3 URI format: %s. URI must match pattern: s3://bucket-name/path/", uri);
    }

    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(IcebergSQLSinkJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static DataGeneratorSource<StockPrice> createDataGenerator(Properties dataGeneratorProperties) {
        double recordsPerSecond = Double.parseDouble(dataGeneratorProperties.getProperty("records.per.sec", "10.0"));
        Preconditions.checkArgument(recordsPerSecond > 0, "Generator records per sec must be > 0");

        LOG.info("Data generator: {} record/sec", recordsPerSecond);
        return new DataGeneratorSource<StockPrice>(new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                TypeInformation.of(StockPrice.class));
    }

    private static String createCatalogStatement(String s3BucketPrefix) {
        return "CREATE CATALOG " + CATALOG_NAME + " WITH (" +
                "'type' =  'iceberg', " +
                "'catalog-impl' = 'org.apache.iceberg.aws.glue.GlueCatalog'," +
                "'io-impl' = 'org.apache.iceberg.aws.s3.S3FileIO'," +
                "'warehouse' = '" + s3BucketPrefix + "')";
    }

    private static String createTableStatement(String sinkTableName) {
        return "CREATE TABLE IF NOT EXISTS " + sinkTableName + " (" +
                "`timestamp` STRING, " +
                "symbol STRING," +
                "price FLOAT," +
                "volumes INT" +
                ") PARTITIONED BY (symbol) ";
    }

    private static IcebergConfig setupIcebergProperties(Properties icebergProperties) {
        String s3BucketPrefix = icebergProperties.getProperty("bucket.prefix");
        String glueDatabase = icebergProperties.getProperty("catalog.db", DEFAULT_DATABASE);
        String glueTable = icebergProperties.getProperty("catalog.table", DEFAULT_TABLE);

        Preconditions.checkNotNull(s3BucketPrefix, "You must supply an s3 bucket prefix for the warehouse.");
        Preconditions.checkNotNull(glueDatabase, "You must supply a database name");
        Preconditions.checkNotNull(glueTable, "You must supply a table name");

        // Validate S3 URI format
        validateURI(s3BucketPrefix);

        LOG.info("Iceberg configuration: bucket={}, database={}, table={}",
                s3BucketPrefix, glueDatabase, glueTable);

        return new IcebergConfig(s3BucketPrefix, glueDatabase, glueTable);
    }

    private static class IcebergConfig {
        final String s3BucketPrefix;
        final String glueDatabase;
        final String glueTable;

        IcebergConfig(String s3BucketPrefix, String glueDatabase, String glueTable) {
            this.s3BucketPrefix = s3BucketPrefix;
            this.glueDatabase = glueDatabase;
            this.glueTable = glueTable;
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. Initialize environments
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2. If running local, we need to enable Checkpoints. Iceberg commits data with every checkpoint
        if (isLocal(env)) {
            // For development, we are checkpointing every 30 second to have data commited faster.
            env.enableCheckpointing(30000);
        }

        // 3. Parse and validate the configuration for the Iceberg sink
        Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        Properties icebergProperties = applicationProperties.get("Iceberg");
        IcebergConfig config = setupIcebergProperties(icebergProperties);

        // 4. Create data generator source, using DataStream API
        Properties dataGenProperties = applicationProperties.get("DataGen");
        DataStream<StockPrice> stockPriceDataStream = env.fromSource(
                createDataGenerator(dataGenProperties),
                WatermarkStrategy.noWatermarks(),
                "DataGen");

        // 5. Convert DataStream to a Table and create view
        Table stockPriceTable = tableEnv.fromDataStream(stockPriceDataStream);
        tableEnv.createTemporaryView("stockPriceTable", stockPriceTable);

        String sinkTableName = CATALOG_NAME + "." + config.glueDatabase + "." + config.glueTable;

        // Create catalog and configure it
        tableEnv.executeSql(createCatalogStatement(config.s3BucketPrefix));
        tableEnv.executeSql("USE CATALOG " + CATALOG_NAME);
        tableEnv.executeSql("CREATE DATABASE IF NOT EXISTS " + config.glueDatabase);
        tableEnv.executeSql("USE " + config.glueDatabase);

        // Create table
        String createTableStatement = createTableStatement(sinkTableName);
        LOG.info("Creating table with statement: {}", createTableStatement);
        tableEnv.executeSql(createTableStatement);

        // 7. Execute SQL operations - Insert data from stock price stream
        String insertQuery = "INSERT INTO " + sinkTableName + " " +
                "SELECT `timestamp`, symbol, price, volumes " +
                "FROM default_catalog.default_database.stockPriceTable";
        TableResult insertResult = tableEnv.executeSql(insertQuery);

        // Keep the job running to continuously insert data
        LOG.info("Application started successfully. Inserting data into Iceberg table: {}", sinkTableName);
    }
}