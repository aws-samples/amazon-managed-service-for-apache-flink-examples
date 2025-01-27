package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.dateFormat;

public class BasicTableJob {

    private static final Logger LOGGER = LogManager.getLogger(BasicTableJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    BasicTableJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.newInstance().build());

        Map<String, Properties> applicationParameters = loadApplicationProperties(env);

        Properties s3Properties = applicationParameters.get("bucket");
        String s3Path = s3Properties.getProperty("name") + '/' + s3Properties.getProperty("path", "output");

        LOGGER.info("s3Path is {}", s3Path);

        // When running locally, enable checkpointing, as filesystem sink rolls files on checkpointing
        // When running on Managed Flink checkpointing is controlled by the application configuration
        if (env instanceof LocalStreamEnvironment) {
            env.enableCheckpointing(5000);
        }

        // Set up the data generator as a dummy source
        long recordPerSecond = 100;
        DataGeneratorSource<StockPrice> source = new DataGeneratorSource<>(
                new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(StockPrice.class));
        DataStream<StockPrice> stockPrices = env.fromSource(source, WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        // Convert the DataStream into a Table
        Table stockPricesTable = tableEnv.fromDataStream(stockPrices);

        // Print the schema for purpose of demonstration only. This will only be printed when the application starts
        // and only visible when you are running locally
        stockPricesTable.printSchema();



        // Filter prices > 50
        // Add the additional columns that will be used for partitioning
        Table filteredStockPricesTable = stockPricesTable.
                select(
                        $("eventTime").as("event_time"),
                        $("ticker"),
                        $("price"),
                        dateFormat($("eventTime"), "yyyy-MM-dd").as("dt"),
                        dateFormat($("eventTime"), "HH").as("hr")
                ).
                where($("price").isGreater(50));

        // Create the sink to S3 table
        tableEnv.createTemporaryView("filtered_stock_prices", filteredStockPricesTable);
        tableEnv.executeSql("CREATE TABLE s3_sink (" +
                 "eventTime TIMESTAMP(3)," +
                 "ticker STRING," +
                 "price DOUBLE," +
                 "dt STRING," +
                 "hr STRING" +
                ") PARTITIONED BY ( dt, hr ) WITH (" +
                "'connector' = 'filesystem'," +
                "'format' = 'json'," +
                "'path' = 's3a://"  + s3Path + "'" +
                ")");

        // Insert the content of the filtered stock prices into the S3 sink table
        filteredStockPricesTable.executeInsert("s3_sink");


        // If you want to print the filtered stock prices, uncomment the following line
        // ATTENTION: only print when developing locally. If you print from an application deployed to Amazon Managed
        // Service for Apache Flink, the output does not appear, but generates overhead for the application to generate it.
//        tableEnv.executeSql("CREATE TABLE print_sink  (" +
//                "eventTime TIMESTAMP," +
//                    "ticker STRING," +
//                    "price DOUBLE," +
//                    "dt STRING," +
//                    "hr STRING" +
//                ") WITH ( 'connector' = 'print')");
//        filteredStockPricesTable.executeInsert("print_sink");
    }
}
