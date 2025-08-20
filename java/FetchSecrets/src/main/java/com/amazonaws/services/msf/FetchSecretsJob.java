package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGenerator;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class FetchSecretsJob {

    private static final Logger LOG = LoggerFactory.getLogger(FetchSecretsJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String DEFAULT_TOPIC = "stock-prices";
    private static final int DEFAULT_RECORDS_PER_SECOND = 10;

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(FetchSecretsJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static DataGeneratorSource<StockPrice> createDataGeneratorSource(Properties dataGenProperties) {
        int recordsPerSecond = Integer.parseInt(dataGenProperties.getProperty("records.per.second", String.valueOf(DEFAULT_RECORDS_PER_SECOND)));
        
        return new DataGeneratorSource<>(
                new StockPriceGenerator(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                TypeInformation.of(StockPrice.class)
        );
    }

    private static KafkaSink<StockPrice> createKafkaSink(Properties outputProperties) {
        String topic = outputProperties.getProperty("topic", DEFAULT_TOPIC);
        
        KafkaRecordSerializationSchema<StockPrice> recordSerializationSchema = 
                KafkaRecordSerializationSchema.<StockPrice>builder()
                        .setTopic(topic)
                        .setKeySerializationSchema(stock -> stock.getSymbol().getBytes())
                        .setValueSerializationSchema(new JsonSerializationSchema<>())
                        .build();

        return KafkaSink.<StockPrice>builder()
                .setBootstrapServers(outputProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(outputProperties)
                .setRecordSerializer(recordSerializationSchema)
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        if (isLocal(env)) {
            env.enableCheckpointing(10_000);
            env.setParallelism(1);
        }

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());
        Properties dataGenProperties = applicationProperties.getOrDefault("DataGen", new Properties());
        Properties outputProperties = applicationProperties.get("Output0");
        outputProperties.putAll(authProperties);

        DataGeneratorSource<StockPrice> source = createDataGeneratorSource(dataGenProperties);
        DataStream<StockPrice> stockPriceStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Stock Price Generator"
        );

        KafkaSink<StockPrice> sink = createKafkaSink(outputProperties);
        stockPriceStream.sinkTo(sink);

        if (isLocal(env)) {
            stockPriceStream.print();
        }

        env.execute("Stock Price to Kafka Job");
    }
}
