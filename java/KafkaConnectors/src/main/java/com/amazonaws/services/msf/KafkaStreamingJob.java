package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;


public class KafkaStreamingJob {

    private static final String DEFAULT_GROUP_ID = "my-group";
    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_SINK_TOPIC = "destination";
    private static final OffsetsInitializer DEFAULT_OFFSETS_INITIALIZER = OffsetsInitializer.earliest();

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamingJob.class);

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
                    Objects.requireNonNull(KafkaStreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }


    private static KafkaSource<String> createKafkaSource(Properties inputProperties) {
        OffsetsInitializer startingOffsetsInitializer = inputProperties.containsKey("startTimestamp") ? OffsetsInitializer.timestamp(
                Long.parseLong(inputProperties.getProperty("startTimestamp"))) : DEFAULT_OFFSETS_INITIALIZER;

        return KafkaSource.<String>builder()
                .setBootstrapServers(inputProperties.getProperty("bootstrap.servers"))
                .setTopics(inputProperties.getProperty("topic", DEFAULT_SOURCE_TOPIC))
                .setGroupId(inputProperties.getProperty("group.id", DEFAULT_GROUP_ID))
                .setStartingOffsets(startingOffsetsInitializer) // Used when the application starts with no state
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperties(inputProperties)
                .build();
    }


    private static KafkaSink<String> createKafkaSink(Properties outputProperties) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(outputProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(outputProperties)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputProperties.getProperty("topic", DEFAULT_SINK_TOPIC))
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .build();
    }


    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);

        LOG.info("Application properties: {}", applicationProperties);

        Properties inputProperties = applicationProperties.get("Input0");
        Properties outputProperties = applicationProperties.get("Output0");

        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());

        inputProperties.putAll(authProperties);
        outputProperties.putAll(authProperties);

        if (!outputProperties.containsKey("transaction.timeout.ms")) {
            outputProperties.setProperty("transaction.timeout.ms", "1000");
        }

        KafkaSource<String> source = createKafkaSource(inputProperties);
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        KafkaSink<String> sink = createKafkaSink(outputProperties);
        input.sinkTo(sink);

        env.execute("Flink Kafka Source and Sink examples");
    }
}
