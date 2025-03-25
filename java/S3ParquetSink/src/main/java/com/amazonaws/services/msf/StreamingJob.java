package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.StockPrice;
import com.amazonaws.services.msf.datagen.StockPriceGeneratorFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private static DataGeneratorSource<StockPrice> getStockPriceDataGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(StockPrice.class));
    }

    private static FileSink<com.amazonaws.services.msf.avro.StockPrice> getParquetS3Sink(String s3UrlPath) {
        return FileSink
                .forBulkFormat(new Path(s3UrlPath), AvroParquetWriters.forSpecificRecord(StockPrice.class))
                // Bucketing
                .withBucketAssigner(new DateTimeBucketAssigner<>("'year='yyyy'/month='MM'/day='dd'/hour='HH/"))
                // Part file rolling - this is actually the default, rolling on checkpoint
                .withRollingPolicy(OnCheckpointRollingPolicy.build())
                .withOutputFileConfig(OutputFileConfig.builder()
                        .withPartSuffix(".parquet")
                        .build())
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
         StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Local dev specific settings
        if (isLocal(env)) {
            // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
            env.enableCheckpointing(30000);
            env.setParallelism(2);
        }

        // Application configuration
        Properties applicationProperties = loadApplicationProperties(env).get("OutputBucket");
        String bucketName = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.name"), "Bucket for S3 not defined");
        String bucketPath = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.path"), "Path in S3 not defined");

        // Build S3 URL. Strip any initial fwd slash from bucket path
        String s3UrlPath =  String.format("s3a://%s/%s", bucketName.trim(),  bucketPath.trim().replaceFirst("^/+", "") );
        LOGGER.info("Output URL: {}", s3UrlPath);

        // Source (data generator)
        DataGeneratorSource<StockPrice> source = getStockPriceDataGeneratorSource();

        // DataStream from source
        DataStream<StockPrice> stockPrices = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        // Sink (Parquet files to S3)
        FileSink<StockPrice> sink = getParquetS3Sink(s3UrlPath);

        stockPrices.map( (price) -> {
            LOGGER.debug(price.toString());
            return price;
        }).sinkTo(sink).name("parquet-s3-sink");

//        stockPrices.print();


        env.execute("Sink Parquet to S3");
    }
}
