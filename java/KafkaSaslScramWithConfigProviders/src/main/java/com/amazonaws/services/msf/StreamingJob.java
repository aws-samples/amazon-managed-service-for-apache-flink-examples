package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaSinkBuilder;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    public static final long RECORDS_PER_SEC = 100;

    public static final String KAFKA_SINK_TOPIC_KEY = "topic";
    public static final String MSKBOOTSTRAP_SERVERS_KEY = "bootstrap.servers";
    public static final String S3_BUCKET_REGION_KEY = "bucket.region";
    public static final String TRUSTSTORE_S3_BUCKET_KEY = "truststore.bucket";
    public static final String TRUSTSTORE_S3_PATH_KEY = "truststore.path";

    public static final String SASL_CREDENTIALS_SECRET_KEY = "credentials.secret";
    public static final String SASL_CREDENTIALS_SECRET_USERNAME_FIELD_KEY = "credentials.secret.username.field";
    public static final String SASL_CREDENTIALS_SECRET_PASSWORD_FIELD_KEY = "credentials.secret.password.field";


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

    private static KafkaSink<String> createKafkaSink(Properties sinkProperties) {
        String bootstrapServers = sinkProperties.getProperty(MSKBOOTSTRAP_SERVERS_KEY);
        String topic = sinkProperties.getProperty(KAFKA_SINK_TOPIC_KEY);
        Preconditions.checkNotNull(bootstrapServers, MSKBOOTSTRAP_SERVERS_KEY + " configuration missing");
        Preconditions.checkNotNull(topic, KAFKA_SINK_TOPIC_KEY + " configuration missing");

        // Base Kafka sink setu[
        KafkaSinkBuilder<String> builder = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setKafkaProducerConfig(sinkProperties)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(topic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE);

        // Set up Config Providers (S3 and Secret Manager)
        builder.setProperty("config.providers", "secretsmanager,s3import");
        builder.setProperty("config.providers.s3import.class", "com.amazonaws.kafka.config.providers.S3ImportConfigProvider");
        builder.setProperty("config.providers.secretsmanager.class", "com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider");


        // Use Config Providers to fetch TrustStore from S3
        String bucketRegion = sinkProperties.getProperty(S3_BUCKET_REGION_KEY);
        String truststoreS3Bucket = sinkProperties.getProperty(TRUSTSTORE_S3_BUCKET_KEY);
        String truststoreS3Path = sinkProperties.getProperty(TRUSTSTORE_S3_PATH_KEY);
        Preconditions.checkNotNull(bucketRegion, S3_BUCKET_REGION_KEY + " configuration missing");
        Preconditions.checkNotNull(truststoreS3Bucket, TRUSTSTORE_S3_BUCKET_KEY + " configuration missing");
        Preconditions.checkNotNull(truststoreS3Path, TRUSTSTORE_S3_PATH_KEY + " configuration missing");

        builder.setProperty("config.providers.s3import.param.region", bucketRegion);
        builder.setProperty("ssl.truststore.location", "${s3import:" + bucketRegion + ":" + truststoreS3Bucket + "/" + truststoreS3Path + "}");
        builder.setProperty("ssl.truststore.password", "changeit"); // Assuming this is a copy of the default JDK TrustStore, the pwd is the default 'changeit`

        // Use config provider to fetch SASL/SCRAM credentials from SecretManager
        String secretsManagerSecretName = sinkProperties.getProperty(SASL_CREDENTIALS_SECRET_KEY);
        String usernameSecretField = sinkProperties.getProperty(SASL_CREDENTIALS_SECRET_USERNAME_FIELD_KEY);
        String passwordSecretField = sinkProperties.getProperty(SASL_CREDENTIALS_SECRET_PASSWORD_FIELD_KEY);

        // Set up SASL_TLS authentication, using the credentials
        builder.setProperty("security.protocol", "SASL_SSL");
        builder.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        String usernameConfigProvider = "${secretsmanager:" + secretsManagerSecretName + ":" + usernameSecretField + "}";
        String passwordConfigProvider = "${secretsmanager:" + secretsManagerSecretName + ":" + passwordSecretField + "}";
        builder.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + usernameConfigProvider + "\" password=\"" + passwordConfigProvider + "\";");


        return builder.build();
    }

    private static DataGeneratorSource<String> createDataGeneratorSource() {
        long recordsPerSecond = RECORDS_PER_SEC;
        return new DataGeneratorSource<>(
                (GeneratorFunction<Long, String>) aLong -> aLong + "-" + RandomStringUtils.randomAlphanumeric(10).toUpperCase(),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                Types.STRING);
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // Load the application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        // Source
        DataGeneratorSource<String> dataGenerator = createDataGeneratorSource();
        DataStream<String> input = env.fromSource(dataGenerator, WatermarkStrategy.noWatermarks(), "data-generator");

        // Sink
        Properties sinkProperties = applicationProperties.get("Output0");
        KafkaSink<String> kafkaSink = createKafkaSink(sinkProperties);

        input.sinkTo(kafkaSink);

        env.execute("Kafka SASL/SCRAM example");

    }

}
