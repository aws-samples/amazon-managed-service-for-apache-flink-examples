package com.amazonaws.services.msf;

import com.amazonaws.services.msf.avro.AirQuality;
import com.amazonaws.services.msf.avro.RoomTemperature;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import org.apache.commons.cli.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sample application to simulate sensors writing air quality and room temperatures to a Kafka topic.
 */
public class Sensors {
    private static final Logger LOG = LoggerFactory.getLogger(Sensors.class);
    private static final String DEFAULT_BOOTSTRAP_SERVER = "localhost:29092";
    private static final String DEFAULT_TOPIC_NAME = "sensor-data";
    private static final Long DEFAULT_SLEEP_TIME_BETWEEN_RECORDS = 1000L;
    private static final String DEFAULT_SCHEMA_REGISTRY_URL = "http://localhost:8085/";

    private final String bootstrapServer;
    private final String topicName;
    private final String schemaRegistry;
    private final long sleepTimeBetweenRecordsMillis;

    public static void main(String[] args) throws Exception {
        Options options = new Options()
                .addOption(Option.builder()
                        .longOpt("bootstrapServer")
                        .hasArg()
                        .desc("Bootstrap server (default: " + DEFAULT_BOOTSTRAP_SERVER +  ")")
                        .build())
                .addOption(Option.builder()
                        .longOpt("topicName")
                        .hasArg()
                        .desc("Topic name (default: " + DEFAULT_TOPIC_NAME + ")")
                        .build())
                .addOption(Option.builder()
                        .longOpt("schemaRegistry")
                        .hasArg()
                        .desc("Confluent Schema Registry URL (default: " + DEFAULT_SCHEMA_REGISTRY_URL + ")")
                        .build())
                .addOption(Option.builder()
                        .longOpt("sleep")
                        .hasArg()
                        .desc("Sleep duration in seconds (default: " + DEFAULT_SLEEP_TIME_BETWEEN_RECORDS + ")")
                        .build());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);
            String bootstrapServer = cmd.getOptionValue("bootstrapServer", DEFAULT_BOOTSTRAP_SERVER);
            String topicName = cmd.getOptionValue("topicName", DEFAULT_TOPIC_NAME);
            String schemaRegistry = cmd.getOptionValue("schemaRegistry", DEFAULT_SCHEMA_REGISTRY_URL);
            long sleepTimeBetweenRecordsMillis = Long.parseLong(cmd.getOptionValue("sleep", String.valueOf(DEFAULT_SLEEP_TIME_BETWEEN_RECORDS)));

            LOG.info("BootstrapServer: {}, TopicName: {}", bootstrapServer, topicName);
            LOG.info("SchemaRegistry: {}", schemaRegistry);
            LOG.info("SleepTimeBetweenRecords: {} ms", sleepTimeBetweenRecordsMillis);

            Sensors sensors = new Sensors(bootstrapServer, topicName, schemaRegistry, sleepTimeBetweenRecordsMillis);
            sensors.produce();
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            formatter.printHelp("Sensors", options);
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Error: sleep parameter must be a valid integer");
            formatter.printHelp("Sensors", options);
            System.exit(1);
        }
    }

    public Sensors(String bootstrapServer, String topicName, String schemaRegistry, long sleepTimeBetweenRecordsMillis) {
        if (bootstrapServer == null || bootstrapServer.trim().isEmpty()) {
            throw new IllegalArgumentException("bootstrapServer cannot be null or empty");
        }
        if (topicName == null || topicName.trim().isEmpty()) {
            throw new IllegalArgumentException("topicName cannot be null or empty");
        }
        if (schemaRegistry == null || schemaRegistry.trim().isEmpty()) {
            throw new IllegalArgumentException("schemaRegistry cannot be null or empty");
        }
        if (sleepTimeBetweenRecordsMillis < 0) {
            throw new IllegalArgumentException("sleepTimeBetweenRecordsMillis must be non-negative");
        }

        this.bootstrapServer = bootstrapServer;
        this.topicName = topicName;
        this.schemaRegistry = schemaRegistry;
        this.sleepTimeBetweenRecordsMillis = sleepTimeBetweenRecordsMillis;
    }

    private void produce() throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServer);
        properties.put("schema.registry.url", schemaRegistry);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", KafkaAvroSerializer.class.getName());
        properties.put("value.subject.name.strategy", RecordNameStrategy.class.getName());

        AtomicBoolean running = new AtomicBoolean(true);

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(properties)) {
            Random random = new Random();

            while (running.get()) {
                // Alternate between AirQuality and RoomTemperature records
                boolean isAirQuality = random.nextBoolean();

                String roomId = "Room-" + random.nextInt(10);
                Instant timestamp = Instant.now();

                // Create record using generated POJOs
                Object record;
                if (isAirQuality) {
                    AirQuality airQuality = new AirQuality();
                    airQuality.setRoom(roomId);
                    airQuality.setQualityIndex(400 + random.nextFloat() * 600); // CO2 between 400-1000 ppm
                    airQuality.setTimestamp(timestamp);
                    record = airQuality;
                } else {
                    RoomTemperature roomTemp = new RoomTemperature();
                    roomTemp.setRoom(roomId);
                    roomTemp.setTemperature(18 + random.nextFloat() * 22); // Temperature between 18-40°C
                    roomTemp.setTimestamp(timestamp);
                    record = roomTemp;
                }

                // Create ProducerRecord with the topic name and record
                ProducerRecord<String, Object> producerRecord =
                        new ProducerRecord<>(topicName, record);

                // Send the record asynchronously
                producer.send(producerRecord, (metadata, exception) -> {
                    if (exception != null) {
                        LOG.error("Error sending record: ", exception);
                        running.set(false);
                    }
                });

                Thread.sleep(sleepTimeBetweenRecordsMillis);
            }
        } catch (InterruptedException e) {
            LOG.warn("Producer interrupted", e);
            throw e;
        }
    }

}
