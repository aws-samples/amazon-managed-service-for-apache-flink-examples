package com.amazonaws.services.msf.windowing;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.runtime.util.EnvironmentInformation;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class TumblingWindowStreamingJob {

    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentInformation.class);

    private static DataStreamSource<String> createKafkaSource(StreamExecutionEnvironment env, String topicName, Properties kafkaProperties)  {

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setProperties(kafkaProperties)
                .setTopics(topicName)
                .setGroupId("kafka-windowing")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        return env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
    }

    private static KafkaSink<String> createKafkaSink(String sinkTopic, Properties kafkaProps)  {
        return KafkaSink.<String>builder()
                .setBootstrapServers(kafkaProps.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(kafkaProps)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(sinkTopic)
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .build();
    }

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


    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final ParameterTool applicationParameters = loadApplicationParameters(args, env);

        String version = EnvironmentInformation.getVersion();
        LOG.error("Flink Version is {}", version);

        String kafkaSourceTopic = applicationParameters.get("kafka-source-topic", "windowing-topic1");
        String kafkaSinkTopic = applicationParameters.get("kafka-sink-topic", "windowing-topic2");
        String brokers = applicationParameters.get("brokers", "localhost:29092");

        LOG.info("kafkaSourceTopic is {}", kafkaSourceTopic);
        LOG.info("kafkaSinkTopic is {}", kafkaSinkTopic);
        LOG.info("brokers is {}", brokers);

        // Create Kafka cluster properties
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", brokers);
        //set transaction timeout if the default is lower than window time
        kafkaProps.setProperty("transaction.timeout.ms","60000");


        DataStreamSource<String> inputDataStream = createKafkaSource(env, kafkaSourceTopic,  kafkaProps);
        KafkaSink<String> sink = createKafkaSink(kafkaSinkTopic, kafkaProps);

        ObjectMapper jsonParser = new ObjectMapper();
        DataStream<Tuple2<String, Integer>> aggregateCount = inputDataStream.map(value -> {
                    JsonNode jsonNode = jsonParser.readValue(value, JsonNode.class);
                    return new Tuple2<>(jsonNode.get("ticker").toString(), 1);
                }).returns(Types.TUPLE(Types.STRING, Types.INT))
                .keyBy(value -> value.f0) // Logically partition the stream for each word
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                .sum(1); // Sum the number of words per partition

        aggregateCount.map(Tuple2::toString).sinkTo(sink);

        env.execute("Tumbling Window Word Count");
    }

}
