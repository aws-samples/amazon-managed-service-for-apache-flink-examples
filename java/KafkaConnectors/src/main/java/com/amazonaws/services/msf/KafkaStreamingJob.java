package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
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


    private static <T> KafkaSource<T> createKafkaSource(Properties inputProperties, final DeserializationSchema<T> valueDeserializationSchema) {
        OffsetsInitializer startingOffsetsInitializer = inputProperties.containsKey("startTimestamp") ? OffsetsInitializer.timestamp(
                Long.parseLong(inputProperties.getProperty("startTimestamp"))) : DEFAULT_OFFSETS_INITIALIZER;

        return KafkaSource.<T>builder()
                .setBootstrapServers(inputProperties.getProperty("bootstrap.servers"))
                .setTopics(inputProperties.getProperty("topic", DEFAULT_SOURCE_TOPIC))
                .setGroupId(inputProperties.getProperty("group.id", DEFAULT_GROUP_ID))
                .setStartingOffsets(startingOffsetsInitializer) // Used when the application starts with no state
                .setValueOnlyDeserializer(valueDeserializationSchema)
                .setProperties(inputProperties)
                .build();
    }


    private static <T> KafkaSink<T> createKafkaSink(Properties outputProperties, final SerializationSchema<T> keySerializationSchema, final SerializationSchema<T> valueSerializationSchema) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(outputProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(outputProperties)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputProperties.getProperty("topic", DEFAULT_SINK_TOPIC))
                        .setKeySerializationSchema(keySerializationSchema)
                        .setValueSerializationSchema(valueSerializationSchema)
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .build();
    }

    private static Properties mergeProperties(Properties properties, Properties authProperties) {
        properties.putAll(authProperties);
        return properties;
    }


    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(1000);

        // Load the application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);

        LOG.info("Application properties: {}", applicationProperties);

        // Get the AuthProperties if present (only relevant when using IAM Auth)
        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());

        // Prepare the Source and Sink properties
        Properties inputProperties = mergeProperties(applicationProperties.get("Input0"), authProperties);
        Properties outputProperties = mergeProperties(applicationProperties.get("Output0"), authProperties);

        // Create and add the Source
        KafkaSource<Stock> source = createKafkaSource(inputProperties, new JsonDeserializationSchema<>(Stock.class));
        DataStream<Stock> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        // Create and add the Sink
        KafkaSink<Stock> sink = createKafkaSink(outputProperties, o -> o.getTicker().getBytes(), new JsonSerializationSchema<>());
        input.sinkTo(sink);

        env.execute("Flink Kafka Source and Sink examples");
    }
}
