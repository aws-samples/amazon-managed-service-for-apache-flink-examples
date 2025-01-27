package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.firehose.sink.KinesisFirehoseSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;


public class FirehoseStreamingJob {

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Create ObjectMapper instance to serialise POJOs into JSONs
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    FirehoseStreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static DataGeneratorSource<StockPrice> getStockPriceDataGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(StockPrice.class));
    }

    private static KinesisFirehoseSink<String> createKinesisFirehoseSink(
            Properties sinkProperties) {
        return KinesisFirehoseSink.<String>builder()
                .setFirehoseClientProperties(sinkProperties)
                .setSerializationSchema(new SimpleStringSchema())
                .setDeliveryStreamName(sinkProperties.getProperty("stream.name"))
                .build();
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);

        // Source
        DataGeneratorSource<StockPrice> source = getStockPriceDataGeneratorSource();

        // DataStream from Source
        DataStream<StockPrice> input = env.fromSource(
                source, WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        KinesisFirehoseSink<String> sink = createKinesisFirehoseSink(applicationProperties.get("OutputStream0"));

        input.map(OBJECT_MAPPER::writeValueAsString).uid("object-to-string-map")
                .sinkTo(sink);

        env.execute("Flink Kinesis Firehose Sink examples");
    }
}
