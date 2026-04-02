package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.connector.kinesis.source.KinesisStreamsSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * A basic Flink Java application to run on Amazon Managed Service for Apache Flink,
 * with Kinesis Data Streams as source and sink.
 */
public class BasicStreamingJob {

    private static final Logger LOGGER = LogManager.getLogger(BasicStreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    BasicStreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static KinesisStreamsSource<String> createSource(Properties inputProperties) {
        return KinesisStreamsSource.<String>builder()
                .setStreamArn(inputProperties.getProperty("stream.arn"))
                .setDeserializationSchema(new SimpleStringSchema())
                .build();
    }

    private static KinesisStreamsSink<String> createSink(Properties outputProperties) {
        Properties sinkClientProperties = new Properties();
        sinkClientProperties.put("aws.region", outputProperties.getProperty("aws.region"));
        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(sinkClientProperties)
                .setStreamName(outputProperties.getProperty("stream.name"))
                .setSerializationSchema(new SimpleStringSchema())
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final Map<String, Properties> applicationParameters = loadApplicationProperties(env);

        KinesisStreamsSource<String> source = createSource(applicationParameters.get("InputStream0"));
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kinesis Source", TypeInformation.of(String.class));

        KinesisStreamsSink<String> sink = createSink(applicationParameters.get("OutputStream0"));
        input.sinkTo(sink);

        env.execute("Flink streaming Java API skeleton");
    }

}
