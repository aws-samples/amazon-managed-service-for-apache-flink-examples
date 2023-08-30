package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class KafkaStreamingJob {

    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_SINK_TOPIC = "destination";

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
                throw new RuntimeException("Unable to load FlinkApplicationProperties properties from runtime properties");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    private static KafkaSource<String> createKafkaSource(ParameterTool applicationProperties) {
        Properties sourceProps = new Properties();
        sourceProps.setProperty("ssl.truststore.location", "/tmp/kafka.client.truststore.jks");

        return KafkaSource.<String>builder()
                .setBootstrapServers(applicationProperties.get("source.bootstrap.servers"))
                .setTopics(applicationProperties.get("source.topic", DEFAULT_SOURCE_TOPIC))
                .setGroupId("my-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new CustomSimpleStringDeserializationSchema()) // Custom deserialization schema is only used to initialize the keystore
                .setProperties(sourceProps)
                .build();
    }

    private static KafkaSink<String> createKafkaSink(ParameterTool applicationProperties) {
        Properties sinkProps = new Properties();
        sinkProps.setProperty("ssl.truststore.location", "/tmp/kafka.client.truststore.jks");

        return KafkaSink.<String>builder()
                .setBootstrapServers(applicationProperties.get("sink.bootstrap.servers"))
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(applicationProperties.get("sink.topic", DEFAULT_SINK_TOPIC))
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new CustomSimpleStringSerializationSchema()) // Custom serialization schema is only used to initialize the keystore
                        .build()
                )
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setKafkaProducerConfig(sinkProps)
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);

        KafkaSource<String> source = createKafkaSource(applicationProperties);
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        KafkaSink<String> sink = createKafkaSink(applicationProperties);
        input.sinkTo(sink);

        env.execute("Flink Kafka Source and Sink using custom keystore");
    }
}
