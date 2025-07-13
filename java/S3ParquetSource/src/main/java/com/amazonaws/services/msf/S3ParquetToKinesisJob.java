package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.StockPrice;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.StreamFormat;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.formats.parquet.avro.AvroParquetReaders;
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

public class S3ParquetToKinesisJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ParquetToKinesisJob.class);

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
                    S3ParquetToKinesisJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static <T extends SpecificRecordBase> FileSource<T> createParquetS3Source(Properties applicationProperties, final Class<T> clazz) {
        String bucketName = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.name"), "Bucket for S3 not defined");
        String bucketPath = Preconditions.checkNotNull(applicationProperties.getProperty("bucket.path"), "Path in S3 not defined");
        int discoveryIntervalSec = Integer.parseInt(applicationProperties.getProperty("bucket.discovery.interval.sec", "30"));

        // Build S3 URL. Strip any initial fwd slash from bucket path
        String s3UrlPath = String.format("s3a://%s/%s", bucketName.trim(), bucketPath.trim().replaceFirst("^/+", ""));
        LOGGER.info("Input URL: {}", s3UrlPath);
        LOGGER.info("Discovery interval: {} sec", discoveryIntervalSec);

        return FileSource
                .forRecordStreamFormat(AvroParquetReaders.forSpecificRecord(clazz), new Path(s3UrlPath))
                .monitorContinuously(Duration.ofSeconds(discoveryIntervalSec))
                .build();
    }

    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties outputProperties, final SerializationSchema<T> serializationSchema) {
        final String outputStreamArn = outputProperties.getProperty("stream.arn");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(outputStreamArn)
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(serializationSchema)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOGGER.info("Application properties: {}", applicationProperties);

        FileSource<StockPrice> source = createParquetS3Source(applicationProperties.get("InputBucket"), StockPrice.class);
        KinesisStreamsSink<String> sink = createKinesisSink(applicationProperties.get("OutputStream0"),new SimpleStringSchema());

        // DataStream from source
        DataStream<StockPrice> stockPrices = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "parquet-source").setParallelism(1);

        // Convert to JSON
        // (We cannot use JsonSerializationSchema on the sink with an AVRO specific object)
        DataStream<String> jsonPrices = stockPrices
                .map(new JsonConverter<>(StockPrice.getClassSchema())).uid("json-converter");

        // Sink JSON to Kinesis
        jsonPrices.sinkTo(sink).name("kinesis-sink");

        // Also print to stdout for local testing
        // (Do not print records to stdout in a production application. This adds overhead and is not visible when deployed on Managed Flink)
        jsonPrices.print().name("stdout-sink");

        env.execute("Source Parquet from S3");
    }


}
