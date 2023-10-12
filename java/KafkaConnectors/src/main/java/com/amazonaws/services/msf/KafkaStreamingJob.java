package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;


public class KafkaStreamingJob {

    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_SINK_TOPIC = "destination";
    private static final String DEFAULT_CLUSTER = "localhost:9092";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamingJob.class);


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
        return KafkaSource.<String>builder()
                .setBootstrapServers(applicationProperties.get("source.bootstrap.servers"))
                .setTopics(applicationProperties.get("source.topic", DEFAULT_SOURCE_TOPIC))
                .setGroupId("my-group")
                .setStartingOffsets(OffsetsInitializer.earliest()) // Used when the application starts with no state
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperties(getKafkaProperties(applicationProperties,"source."))
                .build();
    }

    private static Properties getKafkaProperties(ParameterTool applicationProperties,String startsWith){
        Properties properties = new Properties();
        applicationProperties.getProperties().forEach((key,value)->{
            Optional.ofNullable(key).map(Object::toString).filter(k->{return k.startsWith(startsWith);})
                    .ifPresent(k->{
                            properties.put(k.substring(startsWith.length()),value);
                            });

        });
        LOG.warn(startsWith+" Kafka Properties: "+properties);
        return properties;
    }



    private static KafkaSink<String> createKafkaSink(ParameterTool applicationProperties) {
        return KafkaSink.<String>builder()
                .setBootstrapServers(applicationProperties.get("sink.bootstrap.servers"))
                .setKafkaProducerConfig(getKafkaProperties(applicationProperties,"sink."))
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(applicationProperties.get("sink.topic", DEFAULT_SINK_TOPIC))
                        .setKeySerializationSchema(new SimpleStringSchema())
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .build();
    }


    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);
        LOG.warn("Application properties: {}", applicationProperties.toMap());

        KafkaSource<String> source = createKafkaSource(applicationProperties);
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        KafkaSink<String> sink = createKafkaSink(applicationProperties);

        input.sinkTo(sink);


        env.execute("Flink Kafka Source and Sink examples");
    }
}
