package com.amazonaws.services.msf.windowing;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;

public class WindowStreamingJob {

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowStreamingJob.class);
    public static final Time WINDOW_LENGTH = Time.seconds(30);
    public static final Time STEP_LENGTH = Time.seconds(5);

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    WindowStreamingJob.class.getClassLoader()
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
        LOGGER.info("Application properties: {}", applicationParameters);

        // Set up the data generator as a dummy source
        WatermarkStrategy<StockPrice> watermarkStrategy = WatermarkStrategy
                .<StockPrice>forBoundedOutOfOrderness(Duration.of(10, ChronoUnit.SECONDS))
                .withTimestampAssigner((event, timestamp) -> event.getEventTime().getTime());
        DataStream<StockPrice> input = env.fromSource(
                getStockPriceDataGeneratorSource(), watermarkStrategy, "data-generator").setParallelism(1);

        // Key processing by tick symbol before applying windowing logic
        KeyedStream<StockPrice, String> keyedStream = input.keyBy(StockPrice::getTicker);

        DataStream<String> slidingWindowProcessingTime = keyedStream
                .window(SlidingProcessingTimeWindows.of(WINDOW_LENGTH, STEP_LENGTH))
                .aggregate(new MinAggregate(), new WindowContextEmittingFunction())
                .map(value -> value.getTicker() + String.format(",%.2f,%s", value.getMinPrice(), value.getTimeWindow()));
        slidingWindowProcessingTime.sinkTo(createSink(applicationParameters.get("OutputStream0")));

        DataStream<String> slidingWindowEventTime = keyedStream
                .window(SlidingEventTimeWindows.of(WINDOW_LENGTH, STEP_LENGTH))
                .aggregate(new MinAggregate(), new WindowContextEmittingFunction())
                .map(value -> value.getTicker() + String.format(",%.2f,%s", value.getMinPrice(), value.getTimeWindow()));
        slidingWindowEventTime.sinkTo(createSink(applicationParameters.get("OutputStream1")));

        DataStream<String> tumblingWindowProcessingTime = keyedStream
                .window(TumblingProcessingTimeWindows.of(WINDOW_LENGTH))
                .aggregate(new MinAggregate(), new WindowContextEmittingFunction())
                .map(value -> value.getTicker() + String.format(",%.2f,%s", value.getMinPrice(), value.getTimeWindow()));
        tumblingWindowProcessingTime.sinkTo(createSink(applicationParameters.get("OutputStream2")));

        DataStream<String> tumblingWindowEventTime = keyedStream
                .window(TumblingEventTimeWindows.of(WINDOW_LENGTH))
                .aggregate(new MinAggregate(), new WindowContextEmittingFunction())
                .map(value -> value.getTicker() + String.format(",%.2f,%s", value.getMinPrice(), value.getTimeWindow()));
        tumblingWindowEventTime.sinkTo(createSink(applicationParameters.get("OutputStream3")));

        env.execute("Min Stock Price");
    }

    private static DataGeneratorSource<StockPrice> getStockPriceDataGeneratorSource() {
        long recordPerSecond = 100;
        return new DataGeneratorSource<>(
                new StockPriceGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordPerSecond),
                TypeInformation.of(StockPrice.class));
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

    /**
     * Implementation of min aggregate for demonstration purposes. This specific outcome could be achieved more
     * simply by relying on pre-defined aggregators.
     */
    private static class MinAggregate implements AggregateFunction<StockPrice, Double, Double> {

        @Override
        public Double createAccumulator() {
            return Double.NaN;
        }

        @Override
        public Double add(StockPrice value, Double accumulator) {
            return accumulator.isNaN() || accumulator > value.getPrice()
                    ? value.getPrice()
                    : accumulator;
        }

        @Override
        public Double getResult(Double accumulator) {
            return accumulator;
        }

        @Override
        public Double merge(Double a, Double b) {
            return a.isNaN()
                    ? b
                    : b.isNaN() || b > a
                        ? a
                        : b;
        }

    }

    private static class WindowContextEmittingFunction
            extends ProcessWindowFunction<Double, AggregatedStockPrice, String, TimeWindow> {

        public void process(String key,
                            Context context,
                            Iterable<Double> averages,
                            Collector<AggregatedStockPrice> out) {
            Double minPrice = averages.iterator().next();
            out.collect(new AggregatedStockPrice(context.window(), key, minPrice));
        }
    }

}
