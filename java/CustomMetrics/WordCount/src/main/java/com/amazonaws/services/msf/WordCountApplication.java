package com.amazonaws.services.msf;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.flink.kinesis.shaded.org.apache.flink.connector.aws.config.AWSConfigConstants.AWS_REGION;

/**
 * A basic Managed Service For Apache Flink application with Kinesis data
 * streams as source and sink.
 */
public class WordCountApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordCountApplication.class);

    private static final String APPLICATION_CONFIG_GROUP = "FlinkApplicationProperties";
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String DEFAULT_REGION = "us-east-1";

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Get the Flink application properties from runtime configuration
        Properties applicationProperties = loadApplicationProperties(env).get(APPLICATION_CONFIG_GROUP);

        // Read the input from kinesis source
        DataStreamSource<String> source = env.addSource(createSource(applicationProperties));

        // Split up the lines in pairs (2-tuples) containing: (word,1), and
        // group by the tuple field "0" and sum up tuple field "1"
        DataStream<Tuple2<String, Integer>> wordCountStream = source.flatMap(new Tokenizer()).keyBy(0).sum(1);

        // Serialize the tuple to string format, and publish the output to kinesis sink
        wordCountStream
                .map(Tuple2::toString)
                .sinkTo(createSink(applicationProperties));

        LOGGER.info("Starting flink job: {}", "WordCountApplication");

        // Execute the environment
        env.execute("Flink Word Count Application");
    }

    /**
     * Implements the string tokenizer that splits sentences into words as a
     * user-defined FlatMapFunction. The function takes a line (String) and
     * splits it into multiple pairs in the form of "(word,1)" ({@code Tuple2<String,
     * Integer>}).
     */
    private static final class Tokenizer extends RichFlatMapFunction<String, Tuple2<String, Integer>> {

        private transient Counter counter;

        @Override
        public void open(Configuration config) {
            this.counter = getRuntimeContext().getMetricGroup()
                    .addGroup("kinesisanalytics")
                    .addGroup("Service", "WordCountApplication")
                    .addGroup("Tokenizer")
                    .counter("TotalWords");
        }

        @Override
        public void flatMap(String value, Collector<Tuple2<String, Integer>> out) {
            // normalize and split the line
            String[] tokens = value.toLowerCase().split("\\W+");

            // emit the pairs
            for (String token : tokens) {
                if (token.length() > 0) {
                    counter.inc();
                    out.collect(new Tuple2<>(token, 1));
                }
            }
        }
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
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    WordCountApplication.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

}