package com.amazonaws.services.msf;


import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.util.PropertiesUtil;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.datagen.VehicleEventGeneratorFunction;
import com.amazonaws.services.msf.domain.AggregateVehicleEvent;
import com.amazonaws.services.msf.domain.VehicleEvent;
import com.amazonaws.services.msf.aggregation.AggregateVehicleEventsWindowFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

/**
 * Basic streaming job demonstrating how to define a custom TypeInfo for your records
 */
public class CustomTypeInfoJob {

    private static final Logger LOGGER = LogManager.getLogger(CustomTypeInfoJob.class);

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
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(CustomTypeInfoJob.class.getClassLoader()
                    .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties kinesisClientProperties) {
        String streamArn = kinesisClientProperties.getProperty("stream.arn", "ExampleOutputStream");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(streamArn)
                .setSerializationSchema(new JsonSerializationSchema<>())
                .setPartitionKeyGenerator(element -> String.valueOf(element.hashCode()))
                .setKinesisClientProperties(kinesisClientProperties)
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load application parameters
        final Map<String, Properties> applicationParameters = loadApplicationProperties(env);

        // Define the source
        double recordsPerSecond = PropertiesUtil.getInt(applicationParameters.get("DataGen"), "records.per.sec", 100);
        DataGeneratorSource<VehicleEvent> dataGenSource = new DataGeneratorSource<>(
                new VehicleEventGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                TypeInformation.of(VehicleEvent.class));

        // Define the sink
        KinesisStreamsSink<AggregateVehicleEvent> kinesisSink = createKinesisSink(applicationParameters.get("OutputStream0"));

        /// Define the data flow

        DataStream<AggregateVehicleEvent> aggregateEvents = env
                // Data Generator Source
                .fromSource(dataGenSource, WatermarkStrategy.noWatermarks(), "Data Generator").uid("datagen")
                // Aggregate over 10 second windows
                .keyBy(VehicleEvent::getVin)
                .window(TumblingProcessingTimeWindows.of(Duration.ofSeconds(10)))
                // The ProcessWindowFunction will save the received records in Flink state
                .process(new AggregateVehicleEventsWindowFunction()).returns(AggregateVehicleEvent.class).uid("aggregation");

        // aggregateEvents.print();

        // Sink to Kinesis
        aggregateEvents.sinkTo(kinesisSink).uid("kinesis-sink");

        env.execute("Custom TypeInfo job");
    }

}
