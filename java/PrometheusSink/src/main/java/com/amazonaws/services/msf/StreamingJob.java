package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.datagen.TemperatureSampleGeneratorFunction;
import com.amazonaws.services.msf.domain.TemperatureSample;
import com.amazonaws.services.msf.map.TemperatureSampleToPrometheusTimeSeriesMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.prometheus.sink.PrometheusSink;
import org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeries;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeriesLabelsAndMetricNameKeySelector;
import org.apache.flink.connector.prometheus.sink.aws.AmazonManagedPrometheusWriteRequestSigner;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.apache.flink.connector.prometheus.sink.PrometheusSinkConfiguration.OnErrorBehavior.DISCARD_AND_CONTINUE;

public class StreamingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingJob.class);

    private static final int DEFAULT_GENERATOR_EVENTS_PER_SECOND = 100;
    private static final int DEFAULT_MAX_REQUEST_RETRY = 100;

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }


    private static DataGeneratorSource<TemperatureSample> createDataGeneratorSource(Properties dataGenProperties) {
        double eventsPerSec = (double) PropertiesUtil.getInt(dataGenProperties, "events.per.sec", DEFAULT_GENERATOR_EVENTS_PER_SECOND);
        Preconditions.checkArgument(eventsPerSec > 0, "events.per.sec must be specified and > 0");
        LOGGER.info("Data generator: {} events per second", eventsPerSec);
        return new DataGeneratorSource<>(
                new TemperatureSampleGeneratorFunction(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(eventsPerSec),
                TypeInformation.of(TemperatureSample.class));
    }

    private static Sink<PrometheusTimeSeries> createPrometheusSink(Properties prometheusSinkProperties) {
        String endpointUrl = prometheusSinkProperties.getProperty("endpoint.url");
        Preconditions.checkNotNull(endpointUrl, "endpoint.url must be specified");
        String awsRegion = prometheusSinkProperties.getProperty("aws.region", new DefaultAwsRegionProviderChain().getRegion().toString());
        int maxRequestRetryCount = PropertiesUtil.getInt(prometheusSinkProperties, "max.request.retry", DEFAULT_MAX_REQUEST_RETRY);
        Preconditions.checkArgument(maxRequestRetryCount > 0, "max.request.retry must be > 0");
        LOGGER.info("Prometheus sink: endpoint {}, region {}", endpointUrl, awsRegion);
        return PrometheusSink.builder()
                // Remote-Write endpoint URL (the only mandatory parameter)
                .setPrometheusRemoteWriteUrl(endpointUrl)
                // Set up the request signer for AMP. If you are using a self-managed Prometheus, you can remove this
                .setRequestSigner(new AmazonManagedPrometheusWriteRequestSigner(endpointUrl, awsRegion))
                // Customize retry configuration (not required)
                .setRetryConfiguration(PrometheusSinkConfiguration.RetryConfiguration.builder()
                        .setMaxRetryCount(maxRequestRetryCount).build())
                // Customize error handling behavior (not required)
                .setErrorHandlingBehaviorConfiguration(PrometheusSinkConfiguration.SinkWriterErrorHandlingBehaviorConfiguration.builder()
                        .onMaxRetryExceeded(DISCARD_AND_CONTINUE)
                        .build())
                // Set metric group name to have the metric exported by Managed Flink to CloudWatch
                .setMetricGroupName("kinesisAnalytics")
                .build();
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final Map<String, Properties> applicationParameters = loadApplicationProperties(env);

        /// Define the data flow

        // Data Generator source
        env.fromSource(createDataGeneratorSource(applicationParameters.get("DataGen")), WatermarkStrategy.noWatermarks(), "DataGen")
                .uid("data-gen")
                .setParallelism(1)
                // Transform to PrometheusTimeSeries
                .map(new TemperatureSampleToPrometheusTimeSeriesMapper())
                // Key-by time series to ensure order is retained
                .keyBy(new PrometheusTimeSeriesLabelsAndMetricNameKeySelector())
                // Sink to Prometheus
                .sinkTo(createPrometheusSink(applicationParameters.get("PrometheusSink")))
                .uid("prometheus-sink").name("PrometheusSink");

        env.execute("Prometheus sink example");
    }
}
