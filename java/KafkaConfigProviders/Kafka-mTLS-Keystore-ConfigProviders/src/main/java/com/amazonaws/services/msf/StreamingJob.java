package com.amazonaws.services.msf;


import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceBuilder;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_CONSUMER_GROUP = "flink-app";

    public static final String KAFKA_SOURCE_TOPIC_KEY = "topic";
    public static final String MSKBOOTSTRAP_SERVERS_KEY = "bootstrap.servers";
    public static final String KAFKA_CONSUMER_GROUP_ID_KEY = "group.id";
    public static final String S3_BUCKET_REGION_KEY = "bucket.region";
    public static final String KEYSTORE_S3_BUCKET_KEY = "keystore.bucket";
    public static final String KEYSTORE_S3_PATH_KEY = "keystore.path";
    public static final String TRUSTSTORE_S3_BUCKET_KEY = "truststore.bucket";
    public static final String TRUSTSTORE_S3_PATH_KEY = "truststore.path";
    public static final String KEYSTORE_PASS_SECRET_KEY = "keystore.secret";
    public static final String KEYSTORE_PASS_SECRET_FIELD_KEY = "keystore.secret.field";

    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Get configuration properties from Amazon Managed Service for Apache Flink runtime properties
     * or from a local resource when running locally
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env)
            throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static KafkaSource<String> createKafkaSource(Properties applicationProperties) {
        String bootstrapServers = applicationProperties.getProperty(MSKBOOTSTRAP_SERVERS_KEY);
        Preconditions.checkNotNull(bootstrapServers, MSKBOOTSTRAP_SERVERS_KEY+ " configuration missing");

        KafkaSourceBuilder<String> builder = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(applicationProperties.getProperty(KAFKA_SOURCE_TOPIC_KEY, DEFAULT_SOURCE_TOPIC))
                .setGroupId(applicationProperties.getProperty(KAFKA_CONSUMER_GROUP_ID_KEY, DEFAULT_CONSUMER_GROUP))
                .setStartingOffsets(OffsetsInitializer.earliest()) // Used when the application starts with no state
                .setValueOnlyDeserializer(new SimpleStringSchema());

        builder.setProperty("security.protocol", "SSL");
        configureConnectorPropsWithConfigProviders(builder, applicationProperties);

        return builder.build();
    }

    private static void configureConnectorPropsWithConfigProviders(KafkaSourceBuilder<String> builder, Properties appProperties) {
        // see https://github.com/aws-samples/msk-config-providers

        // define names of config providers:
        builder.setProperty("config.providers", "secretsmanager,s3import");

        // provide implementation classes for each provider:
        builder.setProperty("config.providers.secretsmanager.class", "com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider");
        builder.setProperty("config.providers.s3import.class", "com.amazonaws.kafka.config.providers.S3ImportConfigProvider");

        String region = appProperties.getProperty(S3_BUCKET_REGION_KEY);
        String keystoreS3Bucket = appProperties.getProperty(KEYSTORE_S3_BUCKET_KEY);
        String keystoreS3Path = appProperties.getProperty(KEYSTORE_S3_PATH_KEY);
        String truststoreS3Bucket = appProperties.getProperty(TRUSTSTORE_S3_BUCKET_KEY);
        String truststoreS3Path = appProperties.getProperty(TRUSTSTORE_S3_PATH_KEY);
        String keystorePassSecret = appProperties.getProperty(KEYSTORE_PASS_SECRET_KEY);
        String keystorePassSecretField = appProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY);

        // region, etc..
        builder.setProperty("config.providers.s3import.param.region", region);

        // properties
        builder.setProperty("ssl.truststore.location", "${s3import:" + region + ":" + truststoreS3Bucket + "/" + truststoreS3Path + "}");
        builder.setProperty("ssl.keystore.type", "PKCS12");
        builder.setProperty("ssl.keystore.location", "${s3import:" + region + ":" + keystoreS3Bucket + "/" + keystoreS3Path + "}");
        builder.setProperty("ssl.keystore.password", "${secretsmanager:" + keystorePassSecret + ":" + keystorePassSecretField + "}");
        builder.setProperty("ssl.key.password", "${secretsmanager:" + keystorePassSecret + ":" + keystorePassSecretField + "}");

    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // Load the application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties inputProperties = applicationProperties.get("Input0");

        KafkaSource<String> source = createKafkaSource(inputProperties);
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        input.print(); // Never print/log every record in a production application

        env.execute("Flink Kafka Source from MSK using mTLS");
    }
}
