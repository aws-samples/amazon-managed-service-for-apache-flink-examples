package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGeneratorFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink;
import org.apache.flink.connector.kinesis.sink.PartitionKeyGenerator;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * A Flink application that generates random stock data using DataGeneratorSource
 * and sends it to Kinesis Data Streams and/or Kafka as JSON based on configuration.
 * At least one sink (KinesisSink or KafkaSink) must be configured.
 * The generated data matches the schema used by the Python data generator.
 */
public class DataGeneratorJob {
    private static final Logger LOG = LoggerFactory.getLogger(DataGeneratorJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    // Default values for configuration
    private static final int DEFAULT_RECORDS_PER_SECOND = 10;

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    DataGeneratorJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    /**
     * Create a DataGeneratorSource with configurable rate from DataGen properties
     *
     * @param dataGenProperties Properties from the "DataGen" property group
     * @param generatorFunction The generator function to use for data generation
     * @param typeInformation   Type information for the generated data type
     * @param <T>               The type of data to generate
     * @return Configured DataGeneratorSource
     */
    private static <T> DataGeneratorSource<T> createDataGeneratorSource(
            Properties dataGenProperties,
            GeneratorFunction<Long, T> generatorFunction,
            TypeInformation<T> typeInformation) {

        int recordsPerSecond;
        if (dataGenProperties != null) {
            String recordsPerSecondStr = dataGenProperties.getProperty("records.per.second");
            if (recordsPerSecondStr != null && !recordsPerSecondStr.trim().isEmpty()) {
                try {
                    recordsPerSecond = Integer.parseInt(recordsPerSecondStr.trim());
                } catch (NumberFormatException e) {
                    LOG.error("Invalid records.per.second value: '{}'. Must be a valid integer. ", recordsPerSecondStr);
                    throw e;
                }
            } else {
                LOG.info("No records.per.second configured. Using default: {}", DEFAULT_RECORDS_PER_SECOND);
                recordsPerSecond = DEFAULT_RECORDS_PER_SECOND;
            }
        } else {
            LOG.info("No DataGen properties found. Using default records per second: {}", DEFAULT_RECORDS_PER_SECOND);
            recordsPerSecond = DEFAULT_RECORDS_PER_SECOND;
        }

        Preconditions.checkArgument(recordsPerSecond > 0,
                "Invalid records.per.second value. Must be positive.");


        return new DataGeneratorSource<T>(
                generatorFunction,
                Long.MAX_VALUE, // Generate (practically) unlimited records
                RateLimiterStrategy.perSecond(recordsPerSecond), // Configurable rate
                typeInformation // Explicit type information
        );
    }

    /**
     * Create a Kinesis Sink
     *
     * @param outputProperties      Properties from the "KinesisSink" property group
     * @param serializationSchema   Serialization schema
     * @param partitionKeyGenerator Partition key generator
     * @param <T>                   The type of data to sink
     * @return an instance of KinesisStreamsSink
     */
    private static <T> KinesisStreamsSink<T> createKinesisSink(Properties outputProperties, final SerializationSchema<T> serializationSchema, final PartitionKeyGenerator<T> partitionKeyGenerator
    ) {
        final String outputStreamArn = outputProperties.getProperty("stream.arn");
        return KinesisStreamsSink.<T>builder()
                .setStreamArn(outputStreamArn)
                .setKinesisClientProperties(outputProperties)
                .setSerializationSchema(serializationSchema)
                .setPartitionKeyGenerator(partitionKeyGenerator)
                .build();
    }

    /**
     * Create a KafkaSink
     *
     * @param kafkaProperties           Properties from the "KafkaSink" property group
     * @param recordSerializationSchema Record serialization schema
     * @param <T>                       The type of data to sink
     * @return an instance of KafkaSink
     */
    private static <T> KafkaSink<T> createKafkaSink(Properties kafkaProperties, KafkaRecordSerializationSchema<T> recordSerializationSchema) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(kafkaProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(kafkaProperties)
                .setRecordSerializer(recordSerializationSchema)
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Allows Flink to reuse objects across forwarded operators, as opposed to do a deep copy
        // (this is safe because record objects are never mutated or passed by reference)
        env.getConfig().enableObjectReuse();

        LOG.info("Starting Flink Data Generator Job with conditional sinks");

        // Load application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        // Create a DataGeneratorSource that generates Stock objects using the generic method
        DataGeneratorSource<StockPrice> source = createDataGeneratorSource(
                applicationProperties.get("DataGen"),
                new StockPriceGeneratorFunction(),
                TypeInformation.of(StockPrice.class)
        );

        // Create the data stream from the source
        DataStream<StockPrice> stockPricesStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Data Generator"
        ).uid("data generator");

        // Add a passthrough operator exposing basic metrics
        var outputStream = stockPricesStream.map(new MetricEmitterNoOpMap<>()).uid("metric-emitter");


        // Check if at least one sink is configured
        Properties kinesisProperties = applicationProperties.get("KinesisSink");
        Properties kafkaProperties = applicationProperties.get("KafkaSink");
        boolean hasKinesisSink = kinesisProperties != null;
        boolean hasKafkaSink = kafkaProperties != null;

        if (!hasKinesisSink && !hasKafkaSink) {
            throw new IllegalArgumentException(
                    "At least one sink must be configured. Please provide either 'KinesisSink' or 'KafkaSink' configuration group.");
        }

        // Create Kinesis sink with JSON serialization (only if configured)
        if (hasKinesisSink) {
            PartitionKeyGenerator<StockPrice> partitionKeyGenerator = (record) -> String.valueOf(record.getTicker());
            KinesisStreamsSink<StockPrice> kinesisSink = createKinesisSink(
                    kinesisProperties,
                    // Serialize the Kinesis record as JSON
                    new JsonSerializationSchema<StockPrice>(),
                    // Shard by `ticker`
                    partitionKeyGenerator
            );
            outputStream.sinkTo(kinesisSink).uid("kinesis-sink").disableChaining();
            LOG.info("Kinesis sink configured");
        }

        // Create Kafka sink with JSON serialization (only if configured)
        if (hasKafkaSink) {
            String kafkaTopic = Preconditions.checkNotNull(StringUtils.trimToNull(kafkaProperties.getProperty("topic")), "Kafka topic not defined");
            SerializationSchema<StockPrice> valueSerializationSchema = new JsonSerializationSchema<StockPrice>();
            SerializationSchema<StockPrice> keySerializationSchema = (stockPrice) -> stockPrice.getTicker().getBytes();
            KafkaRecordSerializationSchema<StockPrice> kafkaRecordSerializationSchema =
                    KafkaRecordSerializationSchema.<StockPrice>builder()
                            .setTopic(kafkaTopic)
                            // Serialize the Kafka record value (payload) as JSON
                            .setValueSerializationSchema(valueSerializationSchema)
                            // Partition by `ticker`
                            .setKeySerializationSchema(keySerializationSchema)
                            .build();

            KafkaSink<StockPrice> kafkaSink = createKafkaSink(kafkaProperties, kafkaRecordSerializationSchema);
            outputStream.sinkTo(kafkaSink).uid("kafka-sink").disableChaining();
            LOG.info("Kafka sink configured");
        }

        // Execute the job
        env.execute("Flink Data Generator Job");
    }
}
