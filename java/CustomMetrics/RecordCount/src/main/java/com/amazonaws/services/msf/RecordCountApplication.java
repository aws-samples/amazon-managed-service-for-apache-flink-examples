package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.kinesis.shaded.org.apache.flink.connector.aws.config.AWSConfigConstants.AWS_REGION;

/**
 * A sample Managed Service For Apache Flink application with Kinesis data streams as source and sink with simple filter
 * function.
 */
public class RecordCountApplication {

    private static final Logger LOGGER = LogManager.getLogger(RecordCountApplication.class);

    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";

    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String DEFAULT_REGION = "us-east-1";

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Get the Flink application properties from runtime configuration
        Properties applicationProperties = loadApplicationProperties(env).get(APPLICATION_CONFIG_GROUP);

        DataStreamSource<String> kinesisInput = env.addSource(createSource(applicationProperties));

        // Add the NoOpMapperFunction to publish custom 'ReceivedRecords' metric before filtering
        DataStream<String> noopMapperFunctionBeforeFilter = kinesisInput.map(new NoOpMapperFunction("ReceivedRecords"));

        // Add the FilterFunction to filter the records based on MinSpeed (i.e. 106)
        DataStream<String> kinesisProcessed = noopMapperFunctionBeforeFilter.filter(RecordSchemaHelper::isGreaterThanMinSpeed);

        // Add the NoOpMapperFunction to publish custom 'FilteredRecords' metric after filtering
        DataStream<String> noopMapperFunctionAfterFilter =
                kinesisProcessed.map(new NoOpMapperFunction("FilteredRecords"));

        noopMapperFunctionAfterFilter.sinkTo(createSink(applicationProperties));

        LOGGER.info("Starting flink job: {}", "RecordCountApplication");
        env.execute("RecordCountApplication Job");
    }

    private static FlinkKinesisConsumer<String> createSource(Properties applicationProperties) {

        String inputStreamName = applicationProperties.getProperty("input.stream.name");
        String inputRegion = applicationProperties.getProperty(AWS_REGION, DEFAULT_REGION);
        String inputStartingPosition = applicationProperties.getProperty("flink.stream.initpos");

        Properties sourceProperties = new Properties();
        sourceProperties.put(AWS_REGION, inputRegion);
        sourceProperties.put("flink.stream.initpos", inputStartingPosition);

        LOGGER.info("Configured kinesis source using input stream: {} with initial position: {}",
                inputStreamName, inputStartingPosition);
        return new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(), sourceProperties);
    }

    private static KinesisStreamsSink<String> createSink(Properties applicationProperties) {

        String outputStreamName = applicationProperties.getProperty("output.stream.name");
        String outputRegion = applicationProperties.getProperty(AWS_REGION, DEFAULT_REGION);

        Properties outputProperties = new Properties();
        outputProperties.setProperty(AWS_REGION, outputRegion);

        LOGGER.info("Configured kinesis output using output stream: {}", outputStreamName);
        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setStreamName(outputStreamName)
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

    /**
     * NoOp mapper function which acts as a pass through for the records. It would also publish the custom metric for
     * the number of received records.
     */
    private static class NoOpMapperFunction extends RichMapFunction<String, String> {
        private transient int valueToExpose = 0;
        private final String customMetricName;

        public NoOpMapperFunction(final String customMetricName) {
            this.customMetricName = customMetricName;
        }

        @Override
        public void open(Configuration config) {
            getRuntimeContext().getMetricGroup()
                    .addGroup("kinesisanalytics")
                    .addGroup("Program", "RecordCountApplication")
                    .addGroup("NoOpMapperFunction")
                    .gauge(customMetricName, (Gauge<Integer>) () -> valueToExpose);
        }

        @Override
        public String map(String value) throws Exception {
            valueToExpose++;
            return value;
        }
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    RecordCountApplication.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

}
