package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.table.api.Expressions.*;

public class BasicTableJob {
    private static final Logger LOG = LoggerFactory.getLogger(BasicTableJob.class);

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
                throw new RuntimeException("Unable to load FlinkApplicationProperties properties from the Kinesis Analytics Runtime.");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        ParameterTool applicationParameters = loadApplicationParameters(args, env);

        String kafkaTopic = applicationParameters.get("kafka-topic", "TableTestTopic");
        String brokers = applicationParameters.get("brokers", "");
        String s3Path = applicationParameters.get("s3Path", "");

        LOG.info("kafkaTopic is {}", kafkaTopic);
        LOG.info("brokers is {}", brokers);
        LOG.info("s3Path is {}", s3Path);

        // Create Kafka consumer properties
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", brokers);

        // Process stream using table API
        processTable(env, kafkaTopic, s3Path + "/tableapi", kafkaProps);

        // Process stream using sql API
        processSql(env, kafkaTopic, s3Path + "/sqlapi", kafkaProps);
    }

    public static void processTable(StreamExecutionEnvironment env, String kafkaTopic, String s3Path, Properties kafkaProperties) {
        StreamTableEnvironment streamTableEnvironment = StreamTableEnvironment.create(
                env, EnvironmentSettings.newInstance().build());

        KafkaSource<StockRecord> source = KafkaSource.<StockRecord>builder()
                .setProperties(kafkaProperties)
                .setTopics(kafkaTopic)
                .setGroupId("my-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new KafkaEventDeserializationSchema())
                .build();

        // Obtain stream
        DataStream<StockRecord> events = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // Create the table
        Table table = streamTableEnvironment.fromDataStream(events);

        final Table filteredTable = table.
                select(
                        $("event_time"), $("ticker"), $("price"),
                        dateFormat($("event_time"), "yyyy-MM-dd").as("dt"),
                        dateFormat($("event_time"), "HH").as("hr")
                ).
                where($("price").isGreater(50));

        final String s3Sink = "CREATE TABLE sink_table (" +
                "event_time TIMESTAMP," +
                "ticker STRING," +
                "price DOUBLE," +
                "dt STRING," +
                "hr STRING" +
                ")" +
                " PARTITIONED BY (ticker,dt,hr)" +
                " WITH" +
                "(" +
                " 'connector' = 'filesystem'," +
                " 'path' = 's3a://" + s3Path + "'," +
                " 'format' = 'json'" +
                ") ";

        // Send to s3
        streamTableEnvironment.executeSql(s3Sink);
        filteredTable.executeInsert("sink_table");
    }


    public static void processSql(StreamExecutionEnvironment env, String kafkaTopic, String s3Path, Properties kafkaProperties) {
        StreamTableEnvironment streamTableEnvironment = StreamTableEnvironment.create(
                env, EnvironmentSettings.newInstance().build());

        final String createTableStmt = "CREATE TABLE StockRecord " +
                "(" +
                "event_time TIMESTAMP," +
                "ticker STRING," +
                "price DOUBLE" +
                ")" +
                " WITH (" +
                " 'connector' = 'kafka'," +
                " 'topic' = '" + kafkaTopic + "'," +
                " 'properties.bootstrap.servers' = '" + kafkaProperties.get("bootstrap.servers")
                + "'," +
                " 'properties.group.id' = 'testGroup'," +
                " 'format' = 'json'," +
                " 'scan.startup.mode' = 'earliest-offset'" +
                ")";


        final String s3Sink = "CREATE TABLE sink_table (" +
                "event_time TIMESTAMP," +
                "ticker STRING," +
                "price DOUBLE," +
                "dt STRING," +
                "hr STRING" +
                ")" +
                " PARTITIONED BY (ticker,dt,hr)" +
                " WITH" +
                "(" +
                " 'connector' = 'filesystem'," +
                " 'path' = '" + s3Path + "'," +
                " 'format' = 'json'" +
                ") ";


        streamTableEnvironment.executeSql(createTableStmt);
        streamTableEnvironment.executeSql(s3Sink);

        final String insertSql = "INSERT INTO sink_table SELECT event_time,ticker,price,DATE_FORMAT(event_time, 'yyyy-MM-dd') as dt, " +
                "DATE_FORMAT(event_time, 'HH') as hh FROM StockRecord WHERE price > 50";
        streamTableEnvironment.executeSql(insertSql);
    }


    @Getter
    @Setter
    @ToString
    public static class StockRecord extends Event {
        private Timestamp event_time;
        private String ticker;
        private Double price;
    }

    public static class Event {
        private static final Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd hh:mm:ss")
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) -> Instant.parse(json.getAsString()))
                .create();

        public static Event parseEvent(byte[] event) {

            JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(event)));
            JsonElement jsonElement = Streams.parse(jsonReader);

            // Convert json to POJO, based on the type attribute
            return gson.fromJson(jsonElement, StockRecord.class);
        }
    }

    public static class KafkaEventDeserializationSchema extends AbstractDeserializationSchema<StockRecord> {
        @Override
        public StockRecord deserialize(byte[] bytes) {
            try {
                return (StockRecord) Event.parseEvent(bytes);
            } catch (Exception e) {
                LOG.error("Exception deserializing a record", e);
                return null;
            }
        }

        @Override
        public boolean isEndOfStream(StockRecord event) {
            return false;
        }

        @Override
        public TypeInformation<StockRecord> getProducedType() {
            return TypeExtractor.getForClass(StockRecord.class);
        }
    }
}
