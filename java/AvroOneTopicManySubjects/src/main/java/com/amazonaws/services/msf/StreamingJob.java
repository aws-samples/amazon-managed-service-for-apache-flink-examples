package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.AirQuality;
import com.amazonaws.services.msf.avro.RoomTemperature;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import org.apache.avro.specific.SpecificRecord;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.flink.util.Preconditions;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Sample Flink application that can run in Amazon Managed Service for Apache Flink
 * <p>
 * It simulates a temperature and air quality sensor by writing randomly generated sample records to a Kafka topic.
 * It then consumes those records and processes them according to their type to illustrate how users
 * can consume and process either all or subset of subjects stored in the same topic..
 */
public class StreamingJob {
    private static final int NUM_ROOMS = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingJob.class);
    // Name of the local JSON resource with the application properties in the same format as they are received from the MSF runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Names of the configuration group containing the application properties
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        if (isLocal(env)) {
            env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());
            env.enableCheckpointing(60000);
        }
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);

        Properties applicationProperties = loadApplicationProperties(env).get(APPLICATION_CONFIG_GROUP);
        String bootstrapServers = Preconditions.checkNotNull(applicationProperties.getProperty("bootstrap.servers"), "bootstrap.servers not defined");
        String sourceTopic = Preconditions.checkNotNull(applicationProperties.getProperty("source.topic"), "source.topic not defined");
        String sourceConsumerGroupId = applicationProperties.getProperty("source.consumer.group.id", "avro-one-topic-many-subjects");
        String schemaRegistryUrl = applicationProperties.getProperty("schema.registry.url", "http://localhost:8085");

        ensureTopicExist(bootstrapServers, sourceTopic, 3, (short)1);

        Map<String, Object> schemaRegistryConfig = new HashMap<>();
        setupRoomTemperatureGenerator(bootstrapServers, sourceTopic, schemaRegistryUrl, schemaRegistryConfig, env);
        setupAirQualityGenerator(bootstrapServers, sourceTopic, schemaRegistryUrl, schemaRegistryConfig, env);

        KafkaSource<Option> source = kafkaSource(bootstrapServers, sourceTopic, sourceConsumerGroupId, schemaRegistryUrl, new Properties(), schemaRegistryConfig);
        DataStream<Option> dataStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "source");

        processAirQualityRecords(dataStream);
        processRoomTemperatureRecords(dataStream);

        env.execute("avro-one-topic-many-subjects");
    }

    private static void setupAirQualityGenerator(String bootstrapServers, String sourceTopic, String schemaRegistryUrl, Map<String, Object> schemaRegistryConfig, StreamExecutionEnvironment env) {
        DataGeneratorSource<AirQuality> airQualitySamples = airQualitySamples();
        KafkaSink<AirQuality> airQualitySampleKafkaSink = keyedKafkaSink(bootstrapServers, sourceTopic, schemaRegistryUrl, new Properties(), schemaRegistryConfig);
        env.fromSource(airQualitySamples, WatermarkStrategy.noWatermarks(), "air-quality-samples").sinkTo(airQualitySampleKafkaSink);
    }

    private static void setupRoomTemperatureGenerator(String bootstrapServers, String sourceTopic, String schemaRegistryUrl, Map<String, Object> schemaRegistryConfig, StreamExecutionEnvironment env) {
        DataGeneratorSource<RoomTemperature> roomTemperatureSamples = temperatureSamples();
        KafkaSink<RoomTemperature> temperatureSampleKafkaSink = keyedKafkaSink(bootstrapServers, sourceTopic, schemaRegistryUrl, new Properties(), schemaRegistryConfig);
        env.fromSource(roomTemperatureSamples, WatermarkStrategy.noWatermarks(), "room-temperatures-source").sinkTo(temperatureSampleKafkaSink);
    }

    private static void processRoomTemperatureRecords(DataStream<Option> dataStream) {
        DataStream<RoomTemperature> roomTemperature = dataStream.filter(new FilterFunction<Option>() {
            @Override
            public boolean filter(Option option) throws Exception {
                return option.getValue() instanceof RoomTemperature;
            }
        }).map(new MapFunction<Option, RoomTemperature>() {
            @Override
            public RoomTemperature map(Option option) throws Exception {
                return (RoomTemperature) option.getValue();
            }
        }).keyBy(new KeySelector<RoomTemperature, Object>() {
            @Override
            public Object getKey(RoomTemperature roomTemperature) throws Exception {
                return roomTemperature.getSensorId();
            }
        }).filter(new FilterFunction<RoomTemperature>() {
            @Override
            public boolean filter(RoomTemperature temperatureSample) throws Exception {
                return temperatureSample.getTemperature() > 30.0;
            }
        });
        roomTemperature.print();
    }

    private static void processAirQualityRecords(DataStream<Option> dataStream) {
        DataStream<AirQuality> airQuality = dataStream.filter(new FilterFunction<Option>() {
            @Override
            public boolean filter(Option option) throws Exception {
                return option.getValue() instanceof AirQuality;
            }
        }).map(new MapFunction<Option, AirQuality>() {
            @Override
            public AirQuality map(Option option) throws Exception {
                return (AirQuality) option.getValue();
            }
        }).keyBy(new KeySelector<AirQuality, Object>() {
            @Override
            public Object getKey(AirQuality airQuality) throws Exception {
                return airQuality.getSensorId();
            }
        }).filter(new FilterFunction<AirQuality>() {
            @Override
            public boolean filter(AirQuality airQuality) throws Exception {
               return airQuality.getQualityIndex() > 5;
            }
        });
        airQuality.print();
    }

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from the service runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(StreamingJob.class.getClassLoader().getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application configuration from ");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    /**
     * KafkaSource for any AVRO-generated class (SpecificRecord) Confluent schema registry.
     *
     * @param bootstrapServers    Kafka bootstrap server
     * @param topic               topic name
     * @param consumerGroupId     Kafka Consumer Group ID
     * @param schemaRegistryUrl   Confluent Schema Registry region
     * @param kafkaConsumerConfig configuration passed to the Kafka Consumer
     * @param schemaRegistryConfig Configuration passed into Confluent Schema Registry client
     * @return a KafkaSource instance
     */
    private static KafkaSource<Option> kafkaSource(String bootstrapServers, String topic, String consumerGroupId, String schemaRegistryUrl, Properties kafkaConsumerConfig, Map<String, Object> schemaRegistryConfig) {
        OptionDeserializationSchema d = new OptionDeserializationSchema(schemaRegistryUrl, schemaRegistryConfig);
        return KafkaSource.<Option>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(consumerGroupId)
                .setDeserializer(d)
                .setProperties(kafkaConsumerConfig)
                .build();
    }

    /**
     * KafkaSink for any AVRO-generated class (specific records) using Confluent Schema Registry
     * using a Kafka message key (a String) extracted from the record using a KeySelector.
     *
     * @param <T>                 record type
     * @param bootstrapServers    Kafka bootstrap servers
     * @param topic               topic name
     * @param schemaRegistryUrl   Confluent Schema Registry region
     * @param kafkaProducerConfig configuration passed to the Kafka Producer
     * @return a KafkaSink
     */
    private static <T extends SpecificRecord> KafkaSink<T> keyedKafkaSink(String bootstrapServers, String topic, String schemaRegistryUrl, Properties kafkaProducerConfig, Map<String, Object> schemaRegistryConfig) {
        RecordNameSerializer<T> serializer = new RecordNameSerializer<T>(schemaRegistryUrl, topic, schemaRegistryConfig);
        return KafkaSink.<T>builder().setBootstrapServers(bootstrapServers).setRecordSerializer(serializer).setKafkaProducerConfig(kafkaProducerConfig).build();
    }

    private static float randomSensorReading(float min, float max) {
        float n = new Random().nextFloat();
        return min + n * (max - min);
    }

    private static DataGeneratorSource<RoomTemperature> temperatureSamples() {
        GeneratorFunction<Long, RoomTemperature> generatorFunction = index -> new RoomTemperature(new Random().nextInt(NUM_ROOMS), "room-1", randomSensorReading(17, 40), Instant.now());
        return new DataGeneratorSource<RoomTemperature>(generatorFunction, Integer.MAX_VALUE, RateLimiterStrategy.perSecond(1), TypeInformation.of(RoomTemperature.class));
    }

    private static DataGeneratorSource<AirQuality> airQualitySamples() {
        GeneratorFunction<Long, AirQuality> generatorFunction = index -> new AirQuality(new Random().nextInt(NUM_ROOMS), "room-1", randomSensorReading(1, 10), Instant.now());
        return new DataGeneratorSource<AirQuality>(generatorFunction, Integer.MAX_VALUE, RateLimiterStrategy.perSecond(1), TypeInformation.of(AirQuality.class));
    }

    private static void ensureTopicExist(String bootstrapServers, String topicName, int partitions, short replicationFactor) throws Exception {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient adminClient = AdminClient.create(config)) {
            // Create the topic if it does not exist
            NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
            System.out.println("Topic '" + topicName + "' created successfully.");
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw e;
            }
            // This is fine because we want the topic to exist
        }
    }
}

/**
 * Option type is a generic container to hold deserialized records.
 * It can be useful to introduce fields into this type to handle partitioning
 * strategies and event time extraction. However, for those scenarios to work
 * all subjects should have a standard set of fields.
 */
class Option {
    private Object value;

    public Option() {
    }

    public Option(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return this.value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}

// Custom deserialization schema for handling multiple generic Avro record types
class OptionDeserializationSchema implements KafkaRecordDeserializationSchema<Option> {

    private final String schemaRegistryUrl;
    private transient KafkaAvroDeserializer deserializer;
    private final Map<String, Object> config;

    public OptionDeserializationSchema(String schemaRegistryUrl, Map<String, Object> config) {
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.config = config;
    }

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
        SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 100);

        // With following configuration Avro deserializer should figure out the target type
        // based on the schema subject name
        // (which is the generated class's name for the corresponding avro record type).
        Map<String, Object> config = new HashMap<>();
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy.class.getName());
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        for(String k : this.config.keySet()) {
            config.put(k, this.config.get(k));
        }

        // Create and configure the deserializer
        deserializer = new KafkaAvroDeserializer(schemaRegistryClient, config);
        deserializer.configure(config, false);
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<Option> out) throws IOException {
        Object deserialized = deserializer.deserialize(record.topic(), record.value());
        out.collect(new Option(deserialized));
    }

    @Override
    public TypeInformation<Option> getProducedType() {
        return TypeInformation.of(Option.class);
    }
}

class RecordNameSerializer<T> implements KafkaRecordSerializationSchema<T>
{
    private final String schemaRegistryUrl;
    private final String topic;
    private transient KafkaAvroSerializer serializer;
    private final Map<String, Object> config;

    public RecordNameSerializer(String schemaRegistryUrl, String topic, Map<String, Object> config) {
        this.schemaRegistryUrl = schemaRegistryUrl;
        this.topic = topic;
        this.config = config;
    }

    @Override
    public void open(SerializationSchema.InitializationContext context, KafkaSinkContext sinkContext) throws Exception {
        SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 100);

        Map<String, Object> config = new HashMap<>();
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy.class.getName());
        config.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);

        for(String k : this.config.keySet()) {
           config.put(k, this.config.get(k));
        }

        serializer = new KafkaAvroSerializer(schemaRegistryClient, config);
        serializer.configure(config, false);
    }

    @Nullable
    @Override
    public ProducerRecord<byte[], byte[]> serialize(T t, KafkaSinkContext kafkaSinkContext, Long timestamp) {
        if(t == null) {
            return null;
        }
        // Serialize the record using KafkaAvroSerializer
        byte[] serializedValue = serializer.serialize(topic, t);
        return new ProducerRecord<>(topic, null, timestamp, null, serializedValue);
    }
}
