package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.google.common.base.Preconditions;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.async.AsyncFunction;
import org.apache.flink.streaming.api.functions.async.AsyncRetryStrategy;
import org.apache.flink.streaming.util.retryable.AsyncRetryStrategies;
import org.apache.flink.streaming.util.retryable.RetryPredicates;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RetriesFlinkJob {
        private static final Logger LOGGER = LogManager.getLogger(RetriesFlinkJob.class);
        private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

        private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env)
                throws IOException {
                if (env instanceof LocalStreamEnvironment) {
                        LOGGER.debug("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
                        return KinesisAnalyticsRuntime.getApplicationProperties(
                                RetriesFlinkJob.class.getClassLoader()
                                        .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
                } else {
                        LOGGER.debug("Loading application properties from Amazon Managed Service for Apache Flink");
                        return KinesisAnalyticsRuntime.getApplicationProperties();
                }
        }

        public static void main(String[] args) throws Exception {
                final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

                // Load application parameters
                final Map<String, Properties> applicationParameters = loadApplicationProperties(env);

                Properties endpointProperties = applicationParameters.getOrDefault("EndpointService", new Properties());
                String apiUrl = endpointProperties.getProperty("api.url", "");
                String apiKey = endpointProperties.getProperty("api.key", "");

                AsyncFunction<IncomingEvent, ProcessedEvent> processingFunction = new ProcessingFunction(apiUrl, apiKey);

                DataStreamSource<IncomingEvent> source = env.fromSource(
                                getIncomingEventGeneratorSource(), WatermarkStrategy.noWatermarks(), "data-generator")
                        .setParallelism(1);

                // Defining fixed delay retry strategy
                AsyncRetryStrategy retryStrategy = new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<ProcessedEvent>(
                        3, 1000) // maxAttempts=3, initialDelay=1000 (in ms)
                        .ifResult(RetryPredicates.EMPTY_RESULT_PREDICATE)
                        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
                        .build();

                // Async I/O transformation with retry
                DataStream<ProcessedEvent> processedStream = AsyncDataStream.unorderedWaitWithRetry(
                        source,
                        processingFunction,
                        2000, // Timeout
                        TimeUnit.MILLISECONDS,
                        100, // Capacity
                        retryStrategy);

                Properties outputProperties = applicationParameters.getOrDefault("OutputStream0", new Properties());
                String outputStreamArn = outputProperties.getProperty("stream.arn", "");
                Preconditions.checkArgument(!outputStreamArn.isEmpty(), "Output stream ARN must not be empty");

                processedStream
                        .map(value -> String.format("%s,%s", value.message, value.processed))
                        .sinkTo(createSink(outputProperties));

                LOGGER.debug("Starting flink job: {}", "Async I/O Retries");

                env.execute("Async I/O Job");
        }

        private static DataGeneratorSource<IncomingEvent> getIncomingEventGeneratorSource() {
                long recordPerSecond = 1;
                return new DataGeneratorSource<>(
                        new IncomingEventDataGeneratorFunction(),
                        Long.MAX_VALUE,
                        RateLimiterStrategy.perSecond(recordPerSecond),
                        TypeInformation.of(IncomingEvent.class));
        }

        private static KinesisStreamsSink<String> createSink(Properties outputProperties) {
                String outputStreamArn = outputProperties.getProperty("stream.arn");
                LOGGER.debug("Creating sink for stream: {}", outputStreamArn);
                return KinesisStreamsSink.<String>builder()
                        .setKinesisClientProperties(outputProperties)
                        .setStreamArn(outputStreamArn)
                        .setSerializationSchema(new SimpleStringSchema())
                        .setPartitionKeyGenerator(element -> UUID.randomUUID().toString())
                        .build();
        }
}
