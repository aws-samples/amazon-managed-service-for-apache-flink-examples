package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.aws.config.AWSConfigConstants;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class StreamingJob {

    private static final String DEFAULT_SOURCE_STREAM = "source";
    private static final String DEFAULT_SINK_STREAM = "destination";

    private static final String DEFAULT_AWS_REGION = "eu-west-1";

    /**
     * Get configuration properties from Amazon Managed Service for Apache Flink runtime properties
     * GroupID "FlinkApplicationProperties", or from command line parameters when running locally
     */
    private static ParameterTool loadApplicationParameters(String[] args, StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            return ParameterTool.fromArgs(args);
        } else {
            Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
            Properties flinkProperties = applicationProperties.get("FlinkApplicationProperties");
            if (flinkProperties == null) {
                throw new RuntimeException("Unable to load FlinkApplicationProperties properties from runtime properties");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    private static FlinkKinesisConsumer createKinesisSource(
            ParameterTool applicationProperties) {

        // Properties for Amazon Kinesis Data Streams Source, we need to specify from where we want to consume the data.
        // STREAM_INITIAL_POSITION: LATEST: consume messages that have arrived from the moment application has been deployed
        // STREAM_INITIAL_POSITION: TRIM_HORIZON: consume messages starting from first available in the Kinesis Stream
        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.put(AWSConfigConstants.AWS_REGION, applicationProperties.get("kinesis.region",DEFAULT_AWS_REGION));
        kinesisConsumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        // If EFO consumer is needed, uncomment the following block.
        /*
        kinesisConsumerConfig.put(ConsumerConfigConstants.RECORD_PUBLISHER_TYPE,
                ConsumerConfigConstants.RecordPublisherType.EFO.name());
        kinesisConsumerConfig.put(ConsumerConfigConstants.EFO_CONSUMER_NAME,"my-efo-consumer");
         */

        return new FlinkKinesisConsumer<>(applicationProperties.get("kinesis.source",DEFAULT_SOURCE_STREAM), new SimpleStringSchema(), kinesisConsumerConfig);
    }

    private static KinesisStreamsSink<String> createKinesisSink(
            ParameterTool applicationProperties) {

        Properties sinkProperties = new Properties();
        // Required
        sinkProperties.put(AWSConfigConstants.AWS_REGION, applicationProperties.get("kinesis.region",DEFAULT_AWS_REGION));

        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(sinkProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .setStreamName(applicationProperties.get("kinesis.sink",DEFAULT_SINK_STREAM))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);

        FlinkKinesisConsumer<String> source = createKinesisSource(applicationProperties);
        DataStream<String> input = env.addSource(source, "Kinesis source");

        KinesisStreamsSink<String> sink = createKinesisSink(applicationProperties);
        input.sinkTo(sink);

        env.execute("Flink Kinesis Source and Sink examples");
    }
}