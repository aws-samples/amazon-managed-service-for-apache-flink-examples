package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.TemperatureSample;
import com.amazonaws.services.msf.domain.TemperatureSampleGenerator;
import com.amazonaws.services.msf.kafka.KeyHashKafkaPartitioner;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import org.apache.avro.specific.SpecificRecord;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.formats.avro.glue.schema.registry.GlueSchemaRegistryAvroSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.function.SerializableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.glue.model.Compatibility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static <T extends SpecificRecord> KafkaSink<T> createKafkaSink(
            Properties sinkProperties,
            Properties authProperties,
            KafkaRecordSerializationSchema<T> recordSerializationSchema) {

        Properties kafkaProducerConfig = new Properties();
        kafkaProducerConfig.putAll(sinkProperties);
        kafkaProducerConfig.putAll(authProperties);

        return KafkaSink.<T>builder()
                .setBootstrapServers(sinkProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(kafkaProducerConfig)
                .setRecordSerializer(recordSerializationSchema)
                .build();
    }

    // Create the Kafka Record Serialization schema for an AVRO-specific (generated) record class.
    // This method also extracts the key to be used in the kafka record (it assumes it is a String)
    private static <T extends SpecificRecord> KafkaRecordSerializationSchema<T> kafkaRecordSerializationSchema(
            Class<T> recordClazz,
            SerializableFunction<T, String> keyExtractor,
            Properties schemaRegistryProperties,
            String topic) {

        Map<String, Object> serializerConfig = Map.of(
                // Enable auto registering the schema if it does not exist.
                AWSSchemaRegistryConstants.SCHEMA_AUTO_REGISTRATION_SETTING, true,
                // Explicitly set the compatibility mode when the schema is auto-created (default would be "backward" anyway)
                AWSSchemaRegistryConstants.COMPATIBILITY_SETTING, Compatibility.BACKWARD,
                // GSR Region
                AWSSchemaRegistryConstants.AWS_REGION, schemaRegistryProperties.getProperty("region"),
                // The Registry must exist in the region
                AWSSchemaRegistryConstants.REGISTRY_NAME, schemaRegistryProperties.getProperty("name"));

        // The key is extracted with the provided extractor function and turned into bytes
        SerializationSchema<T> keySerializationSchema = record -> {
            try {
                String key = keyExtractor.apply(record);
                return key.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // Serialize the value using GSR
        SerializationSchema<T> valueSerializationSchema = GlueSchemaRegistryAvroSerializationSchema.forSpecific(
                recordClazz, topic, serializerConfig);

        return KafkaRecordSerializationSchema.<T>builder()
                .setTopic(topic)
                .setKeySerializationSchema(keySerializationSchema)
                .setValueSerializationSchema(valueSerializationSchema)
                // In this example we explicitly set a partitioner to ensure the sink partitions by key in the destination
                // topic. Note that this is not the default behavior of the KafkaSink
                .setPartitioner(new KeyHashKafkaPartitioner<T>())
                .build();
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // For the scope of this example we are disabling operator chaining just to allow observing records flowing in the
        // application. Disabling chaining in production application may seriously impact performances.
        env.disableOperatorChaining();

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());
        Properties outputProperties = applicationProperties.get("Output0");
        Properties schemaRegistryProperties = applicationProperties.get("SchemaRegistry");
        Properties dataGenProperties = applicationProperties.get("DataGen");

        // Set up DataGenerator
        int samplesPerSecond = Integer.parseInt(dataGenProperties.getProperty("samples.per.second", "100"));
        DataGeneratorSource<TemperatureSample> source = new DataGeneratorSource<>(
                new TemperatureSampleGenerator(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(samplesPerSecond),
                TypeInformation.of(TemperatureSample.class)
        );

        // Attach the DataGenerator source
        DataStream<TemperatureSample> temperatureSamples = env.fromSource(
                        source,
                        org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(),
                        "Temperature Generator")
                .uid("temperature-generator");


        // Print the records for the sake of this example.
        // Any output to stdout is visible when running locally, but not when running on Managed Flink
        temperatureSamples.print();

        // Create the record serialization schema, which is specific to the TemperatureSample to be serialized
        // It also defines the topic name and how to extract the key to be used in the kafka record
        KafkaRecordSerializationSchema<TemperatureSample> recordSerializationSchema = kafkaRecordSerializationSchema(
                TemperatureSample.class,
                (SerializableFunction<TemperatureSample, String>) TemperatureSample::getRoom,
                schemaRegistryProperties,
                outputProperties.getProperty("topic"));

        // Attach the sink
        KafkaSink<TemperatureSample> sink = createKafkaSink(outputProperties, authProperties, recordSerializationSchema);
        temperatureSamples.sinkTo(sink).uid("temperature-sink");

        env.execute("Temperature Producer");
    }
}
