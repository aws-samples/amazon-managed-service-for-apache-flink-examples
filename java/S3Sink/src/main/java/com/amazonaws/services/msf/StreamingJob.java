package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class StreamingJob {

    private static final Logger LOGGER = LogManager.getLogger(StreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Create ObjectMapper instance to serialise POJOs into JSONs
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    /**
     *
     * @param s3SinkPath: Path to which application will write data to
     * @return FileSink
     */
    private static FileSink<String> S3Sink(
            String s3SinkPath
    ) {
        return FileSink
                .forRowFormat(new Path(s3SinkPath), new SimpleStringEncoder<String>("UTF-8"))
                .build();
    }

    private static DataGeneratorSource<StockPrice> getStockPriceDataGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(StockPrice.class));
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);

        // Local dev specific settings
        if (isLocal(env)) {
            // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
            env.enableCheckpointing(60000);
            env.setParallelism(2);
        }

        // Retrieve bucket properties
        Properties bucketProperties = loadApplicationProperties(env).get("bucket");
        LOGGER.info("Using bucket properties " + bucketProperties);
        String s3Bucket = Preconditions.checkNotNull(bucketProperties.getProperty("name"), "Target S3 bucket name not defined");

        // Source
        DataGeneratorSource<StockPrice> source = getStockPriceDataGeneratorSource();

        // DataStream from Source
        DataStream<StockPrice> kinesis = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        // Sink
        FileSink<String> sink = S3Sink(String.format("s3a://%s/output", s3Bucket));

        // DataStream to Sink
        kinesis.map(OBJECT_MAPPER::writeValueAsString).uid("object-to-string-map")
                .sinkTo(sink).uid("s3-sink");

        env.execute("Flink S3 Streaming Sink Job");
    }
}
