package com.amazonaws.services.msf.windowing.kinesis;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.assigners.WindowAssigner;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.windowing.windows.Window;
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
    public static final Time JITTER_LENGTH = Time.seconds(5);


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
        DataStream<StockPrice> input = env.fromSource(
                getStockPriceDataGeneratorSource(), WatermarkStrategy.noWatermarks(), "data-generator").setParallelism(1);

        // Assign eventTime watermarks to allow event time processing
        SingleOutputStreamOperator<StockPrice> watermarkedStream = input.assignTimestampsAndWatermarks(WatermarkStrategy
                .<StockPrice>forBoundedOutOfOrderness(Duration.of(10, ChronoUnit.SECONDS))
                .withTimestampAssigner((event, timestamp) -> event.getEventTime().getTime()));

        // Key processing by tick symbol before applying windowing logic
        KeyedStream<Tuple2<String, Double>, String> keyedStream = watermarkedStream.map(value -> { // Parse the JSON
                    return new Tuple2<>(value.getTicker(), value.getPrice());
                }).returns(Types.TUPLE(Types.STRING, Types.DOUBLE))
                .keyBy(v -> v.f0);

        SingleOutputStreamOperator<String> slidingWindowProcessingTime = avgPriceWindow(keyedStream, SlidingProcessingTimeWindows.of(WINDOW_LENGTH, STEP_LENGTH));
        slidingWindowProcessingTime.sinkTo(createSink(applicationParameters.get("OutputStream0")));

        SingleOutputStreamOperator<String> slidingWindowEventTime = avgPriceWindow(keyedStream, SlidingEventTimeWindows.of(WINDOW_LENGTH, STEP_LENGTH));
        slidingWindowEventTime.sinkTo(createSink(applicationParameters.get("OutputStream1")));

        SingleOutputStreamOperator<String> tumblingWindowProcessingTime = avgPriceWindow(keyedStream, TumblingProcessingTimeWindows.of(WINDOW_LENGTH, JITTER_LENGTH));
        tumblingWindowProcessingTime.sinkTo(createSink(applicationParameters.get("OutputStream2")));

        SingleOutputStreamOperator<String> tumblingWindowEventTime = avgPriceWindow(keyedStream, TumblingEventTimeWindows.of(WINDOW_LENGTH, JITTER_LENGTH));
        tumblingWindowEventTime.sinkTo(createSink(applicationParameters.get("OutputStream3")));

        env.execute("Avg Stock Price");
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

    private static SingleOutputStreamOperator<String> avgPriceWindow(KeyedStream<Tuple2<String, Double>, String> keyedStream, WindowAssigner<Object, TimeWindow> windowAssigner) {
        return keyedStream.window(windowAssigner)
                .aggregate(new MinAggregate(), new WindowContextEmittingFunction())
                .map(value -> value.f0 + String.format(",%.2f,%s", value.f1, value.f2));
    }

    /**
     * Implementation of min aggregate for demonstration purposes. This specific outcome could be achieved more
     * simply by relying on pre-defined aggregators.
     */
    private static class MinAggregate implements AggregateFunction<Tuple2<String, Double>, Double, Double> {

        @Override
        public Double createAccumulator() {
            return Double.NaN;
        }

        @Override
        public Double add(Tuple2<String, Double> value, Double accumulator) {
            return accumulator.isNaN() || accumulator > value.f1
                    ? value.f1
                    : accumulator;
        }

        @Override
        public Double getResult(Double accumulator) {
            return accumulator;
        }

        @Override
        public Double merge(Double a, Double b) {
            return a.isNaN() || a > b
                    ? b
                    : a;
        }

    }

    private static class WindowContextEmittingFunction
            extends ProcessWindowFunction<Double, Tuple3<String, Double, Window>, String, TimeWindow> {

        public void process(String key,
                            Context context,
                            Iterable<Double> averages,
                            Collector<Tuple3<String, Double, Window>> out) {
            Double average = averages.iterator().next();
            out.collect(new Tuple3<>(key, average, context.window()));
        }
    }

}
