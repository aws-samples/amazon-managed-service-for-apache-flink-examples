package com.amazonaws.services.msf.windowing.kinesis;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SlidingWindowStreamingJobWithParallelism
{
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_INPUT_STREAM = "input-stream";
    private static final String DEFAULT_OUTPUT_STREAM = "output-stream";

    private static final Logger LOG = LoggerFactory.getLogger(SlidingWindowStreamingJobWithParallelism.class);


    /**
     * Get configuration properties from Amazon Managed Service for Apache Flink runtime properties
     * GroupID "FlinkApplicationProperties", or from command line parameters when running locally
     */
    private static ParameterTool loadApplicationParameters(String[] args, StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            return ParameterTool.fromArgs(args);
        } else {
            Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
            Properties flinkProperties = applicationProperties.get(APPLICATION_CONFIG_GROUP);
            if (flinkProperties == null) {
                throw new RuntimeException("Unable to load FlinkApplicationProperties properties from the Kinesis Analytics Runtime.");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    private static DataStream<String> createSourceFromStaticConfig(StreamExecutionEnvironment env, ParameterTool applicationProperties) {
        Properties inputProperties = new Properties();
        inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION,
                applicationProperties.get("InputStreamRegion", DEFAULT_REGION));
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        return env.addSource(new FlinkKinesisConsumer<>(
                applicationProperties.get("InputStreamName", DEFAULT_INPUT_STREAM)
                , new SimpleStringSchema(),
                inputProperties));
    }

    private static KinesisStreamsSink<String> createSinkFromStaticConfig(ParameterTool applicationProperties) {
        Properties outputProperties = new Properties();
        outputProperties.setProperty(AWSConfigConstants.AWS_REGION,
                applicationProperties.get("OutputStreamRegion", DEFAULT_REGION));

        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setStreamName(applicationProperties.get("OutputStreamName", DEFAULT_OUTPUT_STREAM))
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final ParameterTool applicationParameters = loadApplicationParameters(args, env);
        LOG.warn("Application properties: {}", applicationParameters.toMap());

        DataStream<String> input = createSourceFromStaticConfig(env, applicationParameters);


        ObjectMapper jsonParser = new ObjectMapper();
        input.map(value -> { // Parse the JSON
                    JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
                    return new Tuple2<>(jsonNode.get("ticker").toString(), jsonNode.get("price").asDouble());
                }).returns(Types.TUPLE(Types.STRING, Types.DOUBLE))
                .keyBy(v -> v.f0) // Logically partition the stream per stock symbol
                .window(SlidingProcessingTimeWindows.of(Time.seconds(10), Time.seconds(5)))
                .min(1) // Calculate minimum price per stock over the window
                .setParallelism(env.getParallelism()) // Set parallelism for the min operator
                .map(value -> value.f0 + String.format(",%.2f", value.f1) + "\n")
                .sinkTo(createSinkFromStaticConfig(applicationParameters));

        env.execute("Min Stock Price");
    }

}


