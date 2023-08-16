package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.gsr.avro.TradeCount;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class StreamingJob { private static final Logger LOGGER = LoggerFactory.getLogger(StreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Names of the configuration group containing the application properties
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

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
     * @param inputStream: Name of Kinesis Data Stream
     * @param streamRegion: Region where Kinesis Data Stream
     * @return FlinkKinesisConsumer
     */
    private static  FlinkKinesisConsumer kinesisSource(
            String inputStream,
            String streamRegion) {

        // Properties for Amazon Kinesis Data Streams Source, we need to specify from where we want to consume the data.
        // STREAM_INITIAL_POSITION: LATEST: consume messages that have arrived from the moment application has been deployed
        // STREAM_INITIAL_POSITION: TRIM_HORIZON: consume messages starting from first available in the Kinesis Stream
        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.put(AWSConfigConstants.AWS_REGION, streamRegion);
        kinesisConsumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        // If EFO consumer is needed, uncomment the following block.
        /*
        kinesisConsumerConfig.put(ConsumerConfigConstants.RECORD_PUBLISHER_TYPE,
                ConsumerConfigConstants.RecordPublisherType.EFO.name());
        kinesisConsumerConfig.put(ConsumerConfigConstants.EFO_CONSUMER_NAME,"my-efo-consumer");
         */

        return new FlinkKinesisConsumer<>(inputStream, new SimpleStringSchema(), kinesisConsumerConfig);
    }

    private static FileSink<TradeCount> S3Sink(
            String s3SinkPath
    ) {
        return FileSink
                .forBulkFormat(new Path(s3SinkPath), AvroParquetWriters.forSpecificRecord(TradeCount.class))
                .withBucketAssigner(new DateTimeBucketAssigner<>("'year='yyyy'/month='MM'/day='dd'/hour='HH/"))
                .withOutputFileConfig(OutputFileConfig.builder()
                        .withPartSuffix(".parquet")
                        .build())
                .build();
    }

        public static void main(String[] args) throws Exception {
            // set up the streaming execution environment
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            env.setRuntimeMode(RuntimeExecutionMode.STREAMING);

            // Local dev specific settings
            if (isLocal(env)) {
                // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
                env.enableCheckpointing(60000);
                env.setParallelism(2);
            }

            // Application configuration
            Properties applicationProperties = loadApplicationProperties(env).get(APPLICATION_CONFIG_GROUP);
            String inputStream = Preconditions.checkNotNull(applicationProperties.getProperty("input.stream"), "Input Kinesis Stream not defined");
            String streamRegion = Preconditions.checkNotNull(applicationProperties.getProperty("stream.region"), "Region of Kinesis Streams not Defined");
            String s3SyncPath = Preconditions.checkNotNull(applicationProperties.getProperty("s3.path"), "Path for S3 not defined");

            //Source
            FlinkKinesisConsumer<String> source = kinesisSource(inputStream,streamRegion);

            //Sink
            FileSink<TradeCount> sink = S3Sink(s3SyncPath);

            DataStream<String> kinesis = env.addSource(source).uid("kinesis-source");

            //Mapper to be used for parsing String to JSON
            ObjectMapper jsonParser = new ObjectMapper();

            kinesis.map(value -> { // Parse the JSON
                        JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
                        return new Tuple2<>(jsonNode.get("symbol").toString(), 1);
                    }).returns(Types.TUPLE(Types.STRING, Types.INT)).uid("string-to-tuple-map")
                    .keyBy(v -> v.f0) // Logically partition the stream for each word
                    .window(TumblingProcessingTimeWindows.of(Time.minutes(1)))
                    .sum(1) // Count the appearances by product per partition
                    .map(t -> new TradeCount(t.f0, t.f1))
                    .sinkTo(S3Sink(s3SyncPath))
                    .uid("s3-parquet-sink");

        env.execute("Flink S3 Streaming Sink Job");
    }
}
