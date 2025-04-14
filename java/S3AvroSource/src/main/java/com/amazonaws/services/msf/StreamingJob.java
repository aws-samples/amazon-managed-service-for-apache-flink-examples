package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.StockPrice;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

public class StreamingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Application configuration
        Properties applicationProperties = loadApplicationProperties(env).get("InputBucket");
        String bucketName = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.name"), "Bucket for S3 not defined");
        String bucketPath = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.path"), "Path in S3 not defined");
        // Build S3 URL. Strip any initial fwd slash from bucket path
        String s3UrlPath = String.format("s3a://%s/%s", bucketName.trim(), bucketPath.trim().replaceFirst("^/+", ""));
        LOGGER.info("Input URL: {}", s3UrlPath);


        // Source reading AVRO files into a SpecificRecord
        AvroSpecificRecordBulkFormat<StockPrice> bulkFormat = new AvroSpecificRecordBulkFormat<>(StockPrice.class, StockPrice.SCHEMA$);
        FileSource<StockPrice> source = FileSource
                .forBulkFileFormat(bulkFormat, new Path(s3UrlPath))
                .monitorContinuously(Duration.ofSeconds(10))
                .build();

        // DataStream from source
        DataStream<StockPrice> stockPrices = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "avro-source").setParallelism(1);


        DataStream<String> jsonPrices = stockPrices
                .map(new JsonConverter<>(StockPrice.getClassSchema())).uid("json-converter");

        // FIXME output to Kinesis
        jsonPrices.map((record) -> {
            LOGGER.info("{}", record);
            return record;
        });


        env.execute("Source Avro from S3");
    }
}
