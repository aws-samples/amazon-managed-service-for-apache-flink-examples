package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.deaggregation.KinesisDeaggregatingDeserializationSchemaWrapper;
import com.amazonaws.services.msf.model.StockPrice;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.connector.kinesis.source.serialization.KinesisDeserializationSchema;
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

    // Create a source using a KinesisDeserializationSchema
    private static <T> KinesisStreamsSource<T> createKinesisSource(Properties inputProperties, final KinesisDeserializationSchema<T> kinesisDeserializationSchema) {
        final String inputStreamArn = inputProperties.getProperty("stream.arn");
        return KinesisStreamsSource.<T>builder()
                .setStreamArn(inputStreamArn)
                .setSourceConfig(Configuration.fromMap(Maps.fromProperties(inputProperties)))
                .setDeserializationSchema(kinesisDeserializationSchema)
                .build();
    }

    // Create a sink
    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties outputProperties, final SerializationSchema<T> serializationSchema) {
        final String outputStreamArn = outputProperties.getProperty("stream.arn");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(outputStreamArn)
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(serializationSchema)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.warn("Application properties: {}", applicationProperties);

        // Wrap the deserialization schema
        KinesisDeserializationSchema<StockPrice> deaggregatingKinesisDeserializationSchema =
                // Wrapper which takes care of deaggregation
                new KinesisDeaggregatingDeserializationSchemaWrapper<>(
                        new JsonDeserializationSchema<>(StockPrice.class) // DeserializationSchema to deserialize each deaggregated record
                );
        KinesisStreamsSource<StockPrice> source = createKinesisSource(applicationProperties.get("InputStream0"), deaggregatingKinesisDeserializationSchema);

        // Set up the source
        DataStream<StockPrice> input = env.fromSource(source,
                WatermarkStrategy.noWatermarks(),
                "Kinesis source",
                TypeInformation.of(StockPrice.class));

        // Send the deaggregated records to the sink
        KinesisStreamsSink<StockPrice> sink = createKinesisSink(applicationProperties.get("OutputStream0"), new JsonSerializationSchema<>());

        input.sinkTo(sink);

        env.execute("Kinesis de-aggregating Source");
    }
}
