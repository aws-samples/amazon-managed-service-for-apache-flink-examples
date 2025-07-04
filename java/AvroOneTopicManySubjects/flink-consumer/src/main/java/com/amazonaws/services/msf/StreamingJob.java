package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.AirQuality;
import com.amazonaws.services.msf.avro.RoomTemperature;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Sample Flink application that can run in Amazon Managed Service for Apache Flink
 * <p>
 * It demonstrates how to process records in a Kafka topic with varying schema when
 * they are produced using RecordName subject name strategy in Confluent Schema Registry.
 */
public class StreamingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingJob.class);
    // Name of the local JSON resource with the application properties in the same format as they are received from the MSF runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-consumer-dev.json";

    // Names of the configuration group containing the application properties
    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        if (isLocal(env)) {
            env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new org.apache.flink.configuration.Configuration());
            env.enableCheckpointing(60000);
        }

        Configuration config = loadConfiguration(env);

        ensureTopicExist(config);

        KafkaSource<Option> source = kafkaSource(config);
        DataStream<Option> options = env.fromSource(source, WatermarkStrategy.noWatermarks(), "source");

        DataStream<RoomTemperature> roomTemperature = options
                .filter((FilterFunction<Option>) option -> option.getValue() instanceof RoomTemperature)
                .map((MapFunction<Option, RoomTemperature>) option -> (RoomTemperature) option.getValue())
                .keyBy((KeySelector<RoomTemperature, Object>) RoomTemperature::getSensorId)
                .filter((FilterFunction<RoomTemperature>) temperatureSample -> temperatureSample.getTemperature() > 30.0);

        DataStream<AirQuality> airQuality = options
                .filter((FilterFunction<Option>) option -> option.getValue() instanceof AirQuality)
                .map((MapFunction<Option, AirQuality>) option -> (AirQuality) option.getValue())
                .keyBy((KeySelector<AirQuality, Object>) AirQuality::getSensorId)
                .filter((FilterFunction<AirQuality>) airQuality12 -> airQuality12.getQualityIndex() > 5);


        roomTemperature.print();
        airQuality.print();

        env.execute("flink-consumer");
    }

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application configuration from the service runtime or from a local resource, when the environment is local
     */
    private static Configuration loadConfiguration(StreamExecutionEnvironment env) throws IOException {
        Map<String, Properties> propertyMap;

        if (isLocal(env)) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            propertyMap = KinesisAnalyticsRuntime.getApplicationProperties(StreamingJob.class.getClassLoader().getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application configuration from ");
            propertyMap = KinesisAnalyticsRuntime.getApplicationProperties();
        }

        Properties properties = propertyMap.get(APPLICATION_CONFIG_GROUP);

        return new Configuration(properties);
    }

    /**
     * KafkaSource for any AVRO-generated class (SpecificRecord) Confluent schema registry.
     *
     * @param config    Application configuration
     *
     * @return a KafkaSource instance
     */
    private static KafkaSource<Option> kafkaSource(Configuration config) {
        OptionDeserializationSchema d = new OptionDeserializationSchema(config.getSchemaRegistryUrl());
        return KafkaSource.<Option>builder()
                .setBootstrapServers(config.getBootstrapServers())
                .setTopics(config.getTopic())
                .setGroupId(config.getConsumerGroupId())
                .setDeserializer(d)
                .build();
    }

    private static void ensureTopicExist(Configuration config) throws Exception {
        Properties adminClientConfig = new Properties();

        adminClientConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(adminClientConfig)) {
            // Create the topic if it does not exist
            NewTopic newTopic = new NewTopic(config.getTopic(), 1, (short)1);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
            System.out.println("Topic '" + config.getTopic() + "' created successfully.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // This is fine because we want the topic to exist
                return;
            }
            throw e;
        }
    }
}

