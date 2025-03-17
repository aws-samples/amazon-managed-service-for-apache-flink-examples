package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.sqs.sink.SqsSink;
import org.apache.flink.connector.sqs.sink.SqsSinkElementConverter;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.formats.json.JsonSerializationSchema;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;


public class SQSStreamingJob {

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
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    SQSStreamingJob.class.getClassLoader()
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

    private static <T> SqsSink<T> createSQSSink(
            Properties sinkProperties,
            SqsSinkElementConverter<T> elementConverter) {
        final String sqsUrl = sinkProperties.getProperty("sqs-url");
        return SqsSink.<T>builder()
                .setSqsSinkElementConverter(elementConverter)
                .setSqsUrl(sqsUrl)
                .setSqsClientProperties(sinkProperties)
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

        SqsSinkElementConverter<StockPrice> elementConverter =
                SqsSinkElementConverter.<StockPrice>builder()
                        .setSerializationSchema(new JsonSerializationSchema<>())
                        .build();

        // Sink
        SqsSink<StockPrice> sink = createSQSSink(
                applicationProperties.get("OutputQueue0"),
                elementConverter
        );

        input.sinkTo(sink);

        env.execute("Flink SQS Sink examples");
    }
}
