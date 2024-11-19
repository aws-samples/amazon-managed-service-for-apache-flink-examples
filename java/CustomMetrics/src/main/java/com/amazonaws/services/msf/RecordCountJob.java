package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * A sample Managed Service For Apache Flink application with Kinesis data streams as source and sink with simple filter
 * function.
 */
public class RecordCountJob {

    private static final Logger LOGGER = LogManager.getLogger(RecordCountJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    RecordCountJob.class.getClassLoader()
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

        DataStreamSource<SpeedRecord> input = env.fromSource(
                getSpeedDataGeneratorSource(), WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        // Add the NoOpMapperFunction to publish custom metrics before filtering
        DataStream<SpeedRecord> noopMapperFunctionBeforeFilter = input
                .map(new MetricEmittingMapperFunction("ReceivedRecords"));

        // Add the FilterFunction to filter the records based on MinSpeed (i.e. 106)
        DataStream<SpeedRecord> kinesisProcessed = noopMapperFunctionBeforeFilter.filter(SpeedLimitFilter::isAboveSpeedLimit);

        // Add the NoOpMapperFunction to publish custom metrics after filtering
        DataStream<SpeedRecord> noopMapperFunctionAfterFilter =
                kinesisProcessed.map(new MetricEmittingMapperFunction("FilteredRecords"));

        noopMapperFunctionAfterFilter
                .map(value -> String.format("%s,%.2f", value.id, value.speed))
                .sinkTo(createSink(applicationParameters.get("OutputStream0")));

        LOGGER.info("Starting flink job: {}", "RecordCountJob");

        env.execute("RecordCount Job");
    }

    private static DataGeneratorSource<SpeedRecord> getSpeedDataGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new SpeedRecordGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(SpeedRecord.class));
    }

    private static KinesisStreamsSink<String> createSink(Properties outputProperties) {
        String outputStreamName = outputProperties.getProperty("stream.name");
        return KinesisStreamsSink.<String>builder()
                .setKinesisClientProperties(outputProperties)
                .setStreamName(outputStreamName)
                .setSerializationSchema(new SimpleStringSchema())
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .build();
    }

}
