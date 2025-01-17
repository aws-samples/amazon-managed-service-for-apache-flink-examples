package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.sink.Sink;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.guava31.com.google.common.collect.Maps;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;


public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
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
                    StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static <T> KinesisStreamsSource<T> createKinesisSource(Properties inputProperties, final DeserializationSchema<T> deserializationSchema) {
        final String inputStreamArn = inputProperties.getProperty("stream.arn");
        return KinesisStreamsSource.<T>builder()
                .setStreamArn(inputStreamArn)
                .setSourceConfig(Configuration.fromMap(Maps.fromProperties(inputProperties)))
                .setDeserializationSchema(deserializationSchema)
                .build();
    }

    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties outputProperties, final SerializationSchema<T> serializationSchema) {
        final String outputStreamArn = outputProperties.getProperty("stream.arn");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(outputStreamArn)
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(serializationSchema)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    private static <T> DataStream<T> createDataStream(final StreamExecutionEnvironment env, TypeInformation<T> typeInformation, KinesisStreamsSource<T> source) {
        return env.fromSource(source,
                WatermarkStrategy.noWatermarks(),
                "Kinesis source",
                typeInformation);
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.warn("Application properties: {}", applicationProperties);

        if (applicationProperties.containsKey("SerializationType") && applicationProperties.get("SerializationType").getProperty("type").equals("JSON")) {
            KinesisStreamsSource<Stock> source = createKinesisSource(applicationProperties.get("InputStream0"), new JsonDeserializationSchema<>(Stock.class));
            KinesisStreamsSink<Stock> sink = createKinesisSink(applicationProperties.get("OutputStream0"), new JsonSerializationSchema<>());

            DataStream<Stock> input = createDataStream(env, TypeInformation.of(Stock.class), source);
            input.map(stock -> stock.mutateTicker("AMZN")).sinkTo(sink);

        } else {
            // Fall back to using string instead of JSON with the Stock schema
            KinesisStreamsSource<String> source = createKinesisSource(applicationProperties.get("InputStream0"), new SimpleStringSchema());
            KinesisStreamsSink<String> sink = createKinesisSink(applicationProperties.get("OutputStream0"), new SimpleStringSchema());

            DataStream<String> input = createDataStream(env, TypeInformation.of(String.class), source);

            input.sinkTo(sink);
        }

        env.execute("Flink Kinesis Source and Sink examples");
    }
}
