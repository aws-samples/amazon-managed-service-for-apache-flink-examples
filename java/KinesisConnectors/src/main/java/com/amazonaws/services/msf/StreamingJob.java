package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants.*;
import static org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants.RecordPublisherType.EFO;


public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String DEFAULT_SOURCE_STREAM = "source";
    private static final String DEFAULT_PUBLISHER_TYPE = RecordPublisherType.POLLING.name(); // "POLLING" for standard consumer, "EFO" for Enhanced Fan-Out
    private static final String DEFAULT_EFO_CONSUMER_NAME = "sample-efo-flink-consumer";
    private static final String DEFAULT_SINK_STREAM = "destination";
    private static final String DEFAULT_AWS_REGION = "eu-west-1";

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
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static FlinkKinesisConsumer<String> createKinesisSource(
            Properties inputProperties) {

        // Properties for Amazon Kinesis Data Streams Source, we need to specify from where we want to consume the data.
        // STREAM_INITIAL_POSITION: LATEST: consume messages that have arrived from the moment application has been deployed
        // STREAM_INITIAL_POSITION: TRIM_HORIZON: consume messages starting from first available in the Kinesis Stream
        inputProperties.putIfAbsent(AWSConfigConstants.AWS_REGION, DEFAULT_AWS_REGION);


        // Set up publisher type: POLLING (standard consumer) or EFO (Enhanced Fan-Out)
        inputProperties.putIfAbsent("flink.stream.recordpublisher", DEFAULT_PUBLISHER_TYPE);
        if (inputProperties.getProperty(RECORD_PUBLISHER_TYPE).equals(EFO.name())) {
            inputProperties.putIfAbsent("flink.stream.efo.consumername", DEFAULT_EFO_CONSUMER_NAME);
        }


        return new FlinkKinesisConsumer<>(inputProperties.getProperty("stream.name", DEFAULT_SOURCE_STREAM), new SimpleStringSchema(), inputProperties);
    }

    private static KinesisStreamsSink<String> createKinesisSink(
            Properties outputProperties) {

        // Required
        outputProperties.putIfAbsent(AWSConfigConstants.AWS_REGION, DEFAULT_AWS_REGION);

        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .setStreamName(outputProperties.getProperty("stream.name", DEFAULT_SINK_STREAM))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.warn("Application properties: {}", applicationProperties);

        FlinkKinesisConsumer<String> source = createKinesisSource(applicationProperties.get("InputStream0"));
        DataStream<String> input = env.addSource(source, "Kinesis source");

        KinesisStreamsSink<String> sink = createKinesisSink(applicationProperties.get("OutputStream0"));
        input.sinkTo(sink);

        env.execute("Flink Kinesis Source and Sink examples");
    }
}
