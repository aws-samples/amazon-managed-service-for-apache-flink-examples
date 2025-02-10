/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.table.catalog.CatalogDatabaseImpl;
import org.apache.flink.table.catalog.CatalogDescriptor;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class GlueTableSQLJSONExample {
    // Constants
    private static final String CATALOG_NAME = "glue";
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final Logger LOG = LoggerFactory.getLogger(GlueTableSQLJSONExample.class);

    // Configuration properties
    private static String s3BucketPrefix;
    private static String glueDatabase;
    private static String glueTable;

    public static void main(String[] args) throws Exception {
        // 1. Initialize environments
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2. Load properties and configure environment
        Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        Properties icebergProperties = applicationProperties.get("Iceberg");

        // Configure local development settings if needed
        if (isLocal(env)) {
            env.enableCheckpointing(30000);
            env.setParallelism(2);
        }

        // 3. Setup S3 configuration
        setupGlueTableProperties(icebergProperties);
        Catalog glueCatalog = createGlueCatalog(tableEnv);

        // 4. Create data generator source
        Properties dataGenProperties = applicationProperties.get("DataGen");
        DataStream<StockPrice> stockPriceDataStream = env.fromSource(
                createDataGenerator(dataGenProperties),
                WatermarkStrategy.noWatermarks(),
                "DataGen");

        // 5. Convert DataStream to Table and create view
        Table stockPriceTable = tableEnv.fromDataStream(stockPriceDataStream);
        tableEnv.createTemporaryView("stockPriceTable", stockPriceTable);

        // 6. Create database and define table structure
        glueCatalog.createDatabase(glueDatabase,
                new CatalogDatabaseImpl(Map.of(), "Glue Database"), true);

        String sinkTableName = CATALOG_NAME + "." + glueDatabase + "." + glueTable;

        // Define and create table
        String createTableStatement = "CREATE TABLE IF NOT EXISTS " + sinkTableName + "(" +
                "price DOUBLE, " +
                "ticker STRING," +
                "eventtime TIMESTAMP(3)" +
                ");";
        tableEnv.executeSql(createTableStatement);

        // 7. Execute SQL operations
        // Insert data from stock price stream
        String insertQuery = "INSERT INTO " + sinkTableName +
                " SELECT price, ticker, eventtime FROM stockPriceTable";
        TableResult insertResult = tableEnv.executeSql(insertQuery);
        insertResult.await();

        // Query the results
        String selectQuery = "SELECT * from " + sinkTableName + ";";
        TableResult selectResults = tableEnv.executeSql(selectQuery);
        selectResults.print();

        // 8. Optionally Cleanup resources
//        glueCatalog.dropTable(new ObjectPath(glueDatabase, glueTable), false);
//        glueCatalog.dropDatabase(glueDatabase, false);
    }

    private static void setupGlueTableProperties(Properties icebergProperties) {
        s3BucketPrefix = icebergProperties.getProperty("bucket.prefix");
        glueDatabase = icebergProperties.getProperty("catalog.db");
        glueTable = icebergProperties.getProperty("catalog.table");
        Preconditions.checkNotNull(s3BucketPrefix, "You must supply an s3 bucket ARN for the warehouse.");
        Preconditions.checkNotNull(glueDatabase, "You must supply a database name");
        Preconditions.checkNotNull(glueTable, "You must supply a table name");
        // check if it's a valid ARN
        validateURI(s3BucketPrefix);
    }

    private static DataGeneratorSource<StockPrice> createDataGenerator(Properties dataGeneratorProperties) {
        double recordsPerSecond = Double.parseDouble(dataGeneratorProperties.getProperty("records.per.sec", "10.0"));
        Preconditions.checkArgument(recordsPerSecond > 0, "Generator records per sec must be > 0");

        return new DataGeneratorSource<StockPrice>(new StockPriceGeneratorFunction(),
                100,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                TypeInformation.of(StockPrice.class));
    }

    /**
     * Defines a config object with Glue specific catalog and io implementations
     * Then, uses that to create the Flink catalog
     */
    private static Catalog createGlueCatalog(StreamTableEnvironment tableEnv) {

         Configuration conf = new Configuration();
         conf.setString("warehouse", s3BucketPrefix);
         conf.setString("catalog-impl", "org.apache.iceberg.aws.glue.GlueCatalog");
         conf.setString("type", "iceberg");
         conf.setString("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
         conf.setString("catalog-name", CATALOG_NAME);

         CatalogDescriptor descriptor = CatalogDescriptor.of(CATALOG_NAME, conf);

         tableEnv.createCatalog(CATALOG_NAME, descriptor);
         return tableEnv.getCatalog(CATALOG_NAME).get();
    }

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime
     * or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(GlueTableSQLJSONExample.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void validateURI(String uri) {
        String s3UriPattern = "^s3://([a-z0-9.-]+)(/[a-z0-9-_/]+/?)$";
        Preconditions.checkArgument(uri != null && uri.matches(s3UriPattern),
                "Invalid S3 URI format: %s. URI must match pattern: s3://bucket-name/path/", uri);
    }
}
