package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * A sample Managed Service For Apache Flink application with Data Gen as a
 * source and Kinesis data streams as the sinks
 * demonstrating how to use Side Ouputs to split a stream.
 */
public class SideOutputsFlinkJob {

    private static final Logger LOGGER = LogManager.getLogger(SideOutputsFlinkJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Defining Output Tag to be used for invalid events
    private static final OutputTag<IncomingEvent> invalidEventsTag = new OutputTag<IncomingEvent>("invalid-events") {
    };

    /**
     * Load application properties from Amazon Managed Service for Apache Flink
     * runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env)
            throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    SideOutputsFlinkJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void main(String[] args) throws Exception {

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final Map<String, Properties> applicationParameters = loadApplicationProperties(env);

        DataStreamSource<IncomingEvent> source = env.fromSource(
                getIncomingEventGeneratorSource(), WatermarkStrategy.noWatermarks(), "data-generator")
                .setParallelism(1);

        // Validate stream for invalid messages
        SingleOutputStreamOperator<Tuple2<IncomingEvent, Boolean>> validatedStream = source
                .map(incomingEvent -> {
                    boolean isPoisoned = "Poison".equals(incomingEvent.message);
                    return Tuple2.of(incomingEvent, isPoisoned);
                }, TypeInformation.of(new TypeHint<Tuple2<IncomingEvent, Boolean>>() {
                }));

        // Split the stream based on validation
        SingleOutputStreamOperator<IncomingEvent> mainStream = validatedStream
                .process(new ProcessFunction<Tuple2<IncomingEvent, Boolean>, IncomingEvent>() {
                    @Override
                    public void processElement(Tuple2<IncomingEvent, Boolean> value, Context ctx,
                            Collector<IncomingEvent> out) throws Exception {
                        if (value.f1) {
                            // Invalid event (true), send to DLQ sink
                            ctx.output(invalidEventsTag, value.f0);
                        } else {
                            // Valid event (false), continue processing
                            out.collect(value.f0);
                        }
                    }
                });
        DataStream<IncomingEvent> exceptionStream = mainStream.getSideOutput(invalidEventsTag);

        // Send messages to appropriate sink
        exceptionStream
                .map(value -> String.format("%s", value.message))
                .sinkTo(createSink(applicationParameters.get("DLQOutputStream")));

        mainStream
                .map(value -> String.format("%s", value.message))
                .sinkTo(createSink(applicationParameters.get("ProcessedOutputStream")));

        LOGGER.info("Starting flink job: {}", "Side Outputs");

        env.execute("SideOutputs Job");
    }

    private static DataGeneratorSource<IncomingEvent> getIncomingEventGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new IncomingEventDataGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(IncomingEvent.class));
    }

    private static KinesisStreamsSink<String> createSink(Properties outputProperties) {
        String outputStreamArn = outputProperties.getProperty("stream.arn");
        LOGGER.info("Creating sink for stream: {}", outputStreamArn);
        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setStreamArn(outputStreamArn)
                .setSerializationSchema(new SimpleStringSchema())
                .setPartitionKeyGenerator(element -> UUID.randomUUID().toString())
                .build();
    }

}
