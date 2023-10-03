    package com.amazonaws.services.msf;
     
    import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;

    import java.io.IOException;
    import java.util.Map;
    import java.util.Properties;
    import org.apache.flink.api.common.functions.FilterFunction;
    import org.apache.flink.api.common.functions.RichMapFunction;
    import org.apache.flink.api.common.serialization.SimpleStringSchema;
    import org.apache.flink.configuration.Configuration;
    import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
    import org.apache.flink.metrics.Gauge;
    import org.apache.flink.streaming.api.datastream.DataStream;
    import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
    import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
    import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
     
    /**
     * A sample Managed Service For Apache Flink application with Kinesis data streams as source and sink with simple filter
     * function.
     */
    public class RecordCountApplication {
     
        private static final Logger LOG = LoggerFactory.getLogger(RecordCountApplication.class);
        private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

        private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
            if (env instanceof LocalStreamEnvironment) {
                return KinesisAnalyticsRuntime.getApplicationProperties(RecordCountApplication.class.getClassLoader()
                        .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
            } else {
                return KinesisAnalyticsRuntime.getApplicationProperties();
            }
        }
     
        public static void main(String[] args) throws Exception {
            StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

            Map<String, Properties> runConfigurations = loadApplicationProperties(env);

            // Get the Flink application properties from runtime configuration
            Properties inputConfig = runConfigurations.get("FlinkApplicationProperties");
            String inputStreamName = inputConfig.getProperty("input.stream.name");
            String outputStreamName = inputConfig.getProperty("output.stream.name");
            String inputRegion = inputConfig.getProperty("aws.region");
            String inputStartingPosition = inputConfig.getProperty("flink.stream.initpos");
            Properties applicationProperties = new Properties();
            applicationProperties.put("aws.region", inputRegion);
            applicationProperties.put("flink.stream.initpos", inputStartingPosition);
     
            // Add the Kinesis Consumer as input
            DataStream<String> kinesisInput =
                    env.addSource(new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(), applicationProperties));
     
            // Add the NoOpMapperFunction to publish custom 'ReceivedRecords' metric before filtering
            DataStream<String> noopMapperFunctionBeforeFilter = kinesisInput.map(new NoOpMapperFunction("ReceivedRecords"));
     
            // Add the FilterFunction to filter the records based on MinSpeed (i.e. 106)
            DataStream<String> kinesisProcessed = noopMapperFunctionBeforeFilter.filter(new FilterFunction<String>() {
                public boolean filter(String value) throws Exception {
                    return RecordSchemaHelper.isGreaterThanMinSpeed(value);
                }
            });
     
            // Add the NoOpMapperFunction to publish custom 'FilteredRecords' metric after filtering
            DataStream<String> noopMapperFunctionAfterFilter =
                    kinesisProcessed.map(new NoOpMapperFunction("FilteredRecords"));
     
            // Add the Kinesis sink as output
            KinesisStreamsSink<String> sink = KinesisStreamsSink.<String>builder()
                    .setKinesisClientProperties(applicationProperties)
                    .setSerializationSchema(new SimpleStringSchema())
                    .setStreamName(outputStreamName)
                    .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                    .build();

            noopMapperFunctionAfterFilter.sinkTo(sink);
     
            LOG.info("Starting flink job: {} with using input kinesis stream: {} with initial position: {} and output"
                             + " kinesis stream: {}", "RecordCountApplication",
                    inputStreamName, inputStartingPosition, outputStreamName);
            env.execute("RecordCountApplication Job");
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
    }