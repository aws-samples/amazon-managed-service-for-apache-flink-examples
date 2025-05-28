package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.StockPrice;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.json.JsonSerializationSchema;
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


    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties outputProperties, final SerializationSchema<T> serializationSchema) {
        final String outputStreamArn = outputProperties.getProperty("stream.arn");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(outputStreamArn)
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(serializationSchema)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    private static <T extends SpecificRecord> FileSource<T> createAvroFileSource(Properties sourceProperties, Class<T> avroRecordClass, Schema avroSchema) {
        String bucketName = Preconditions.checkNotNull(sourceProperties.getProperty("bucket.name"), "Bucket for S3 not defined");
        String bucketPath = Preconditions.checkNotNull(sourceProperties.getProperty("bucket.path"), "Path in S3 not defined");

        // Build S3 URL. Strip any initial fwd slash from bucket path
        String s3UrlPath = String.format("s3a://%s/%s", bucketName.trim(), bucketPath.trim().replaceFirst("^/+", ""));
        LOGGER.info("Input URL: {}", s3UrlPath);

        // A custom BulkFormat is required to read AVRO files
        AvroSpecificRecordBulkFormat<T> bulkFormat = new AvroSpecificRecordBulkFormat<>(avroRecordClass, avroSchema);

        return FileSource.forBulkFileFormat(bulkFormat, new Path(s3UrlPath))
                .monitorContinuously(Duration.ofSeconds(10))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Application configuration
        Map<String, Properties> applicationPropertiesMap = loadApplicationProperties(env);

        // Source reading AVRO files into a SpecificRecord
        FileSource<StockPrice> avroFileSource = createAvroFileSource(applicationPropertiesMap.get("InputBucket"), StockPrice.class, StockPrice.SCHEMA$);

        // DataStream from source
        DataStream<StockPrice> stockPrices = env.fromSource(
                avroFileSource, WatermarkStrategy.noWatermarks(), "avro-source", TypeInformation.of(StockPrice.class)).setParallelism(1);

        // Convert the AVRO record into a String containing JSON
        DataStream<String> jsonStockPrices = stockPrices.map(new JsonConverter<>(StockPrice.SCHEMA$));

        // Output the Strings to Kinesis
        // (You cannot use JsonSerializationSchema to convert AVRO specific records into JSON, directly)
        KinesisStreamsSink<String> kinesisSink = createKinesisSink(applicationPropertiesMap.get("OutputStream0"), new SimpleStringSchema());

        // Attach the sink
        jsonStockPrices.sinkTo(kinesisSink);

        // Also print the output
        // (This is for illustration purposes only and used when running locally. No output is printed when running on Managed Flink)
        jsonStockPrices.print();

        env.execute("Source Avro from S3");
    }
}
