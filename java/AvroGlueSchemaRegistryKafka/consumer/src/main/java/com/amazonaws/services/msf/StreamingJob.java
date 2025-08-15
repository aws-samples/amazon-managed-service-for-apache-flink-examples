package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.TemperatureSample;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import com.amazonaws.services.schemaregistry.utils.AvroRecordType;
import org.apache.avro.specific.SpecificRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.formats.avro.glue.schema.registry.GlueSchemaRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static <T extends SpecificRecord> KafkaSource<T> createKafkaSource(
            Properties sourceProperties,
            Properties authProperties,
            KafkaRecordDeserializationSchema<T> kafkaRecordDeserializationSchema) {

        Properties kafkaConsumerConfig = new Properties();
        kafkaConsumerConfig.putAll(sourceProperties);
        kafkaConsumerConfig.putAll(authProperties);

        return KafkaSource.<T>builder()
                .setBootstrapServers(sourceProperties.getProperty("bootstrap.servers"))
                .setTopics(sourceProperties.getProperty("topic"))
                .setGroupId(sourceProperties.getProperty("group.id"))
                // If the job starts with no state, use the latest committed offset
                // If the commited offset is also not available, start from latest
                .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
                .setDeserializer(kafkaRecordDeserializationSchema)
                .setProperties(kafkaConsumerConfig)
                .build();
    }

    // Create a Kafka Record Deserialization Schema using GSR
    // to extract the record value into an AVRO-specific (generated) record class
    private static <T extends SpecificRecord> KafkaRecordDeserializationSchema<T> kafkaRecordDeserializationSchema(Properties schemaRegistryProperties, Class<T> recordClazz) {
        Map<String, Object> deserializerConfig = Map.of(
                AWSSchemaRegistryConstants.AVRO_RECORD_TYPE, AvroRecordType.SPECIFIC_RECORD.getName(),
                AWSSchemaRegistryConstants.AWS_REGION, schemaRegistryProperties.getProperty("region"),
                AWSSchemaRegistryConstants.REGISTRY_NAME, schemaRegistryProperties.getProperty("name"));

        DeserializationSchema<T> legacyDeserializationSchema = GlueSchemaRegistryAvroDeserializationSchema.forSpecific(recordClazz, deserializerConfig);
        return KafkaRecordDeserializationSchema.valueOnly(legacyDeserializationSchema);
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // For the scope of this example we are disabling operator chaining just to allow observing records flowing in the
        // application. Disabling chaining in production application may seriously impact performances.
        env.disableOperatorChaining();

        if(isLocal(env)) {
            // When running locally, enable checkpoints: the KafkaSource commits offsets on checkpoint only.
            // When running on Managed Flink checkpoints are controlled by the application configuration.
            env.enableCheckpointing(10_000);
        }

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());
        Properties inputProperties = applicationProperties.get("Input0");
        Properties schemaRegistryProperties = applicationProperties.get("SchemaRegistry");

        // Set up the deserialization schema and the Kafka source
        KafkaRecordDeserializationSchema<TemperatureSample> recordDeserializationSchema = kafkaRecordDeserializationSchema(schemaRegistryProperties, TemperatureSample.class);
        KafkaSource<TemperatureSample> source = createKafkaSource(inputProperties, authProperties, recordDeserializationSchema);

        // Attach the source
        DataStream<TemperatureSample> temperatureSamples = env.fromSource(
                        source,
                        WatermarkStrategy.noWatermarks(),
                        "Temperature Samples source")
                .uid("temperature-samples-source");


        // We print the records for the sake of this example.
        // Any output to stdout is visible when running locally, but not when running on Managed Flink
        temperatureSamples.print().uid("print-sink");

        env.execute("Temperature Samples Consumer");
    }
}
