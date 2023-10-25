package com.amazonaws.services.msf;


import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.KafkaSourceBuilder;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_CONSUMER_GROUP = "flink-app";

    public static final String MSKBOOTSTRAP_SERVERS_KEY = "MSKBootstrapServers";
    public static final String KAFKA_SOURCE_TOPIC_KEY = "KafkaSourceTopic";
    public static final String KAFKA_CONSUMER_GROUP_ID_KEY = "KafkaConsumerGroupId";
    public static final String S3_BUCKET_REGION_KEY = "S3BucketRegion";
    public static final String KEYSTORE_S3_BUCKET_KEY = "KeystoreS3Bucket";
    public static final String KEYSTORE_S3_PATH_KEY = "KeystoreS3Path";
    public static final String TRUSTSTORE_S3_BUCKET_KEY = "TruststoreS3Bucket";
    public static final String TRUSTSTORE_S3_PATH_KEY = "TruststoreS3Path";
    public static final String KEYSTORE_PASS_SECRET_KEY = "KeystorePassSecret";
    public static final String KEYSTORE_PASS_SECRET_FIELD_KEY = "KeystorePassSecretField";

    /**
     * Get configuration properties from Amazon Managed Service for Apache Flink runtime properties
     * GroupID "FlinkApplicationProperties", or from command line parameters when running locally
     */
    private static ParameterTool loadApplicationParameters(String[] args, StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            return ParameterTool.fromArgs(args);
        } else {
            Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
            Properties flinkProperties = applicationProperties.get("FlinkApplicationProperties");
            if (flinkProperties == null) {
                throw new RuntimeException("Unable to load FlinkApplicationProperties properties from runtime properties");
            }
            Map<String, String> map = new HashMap<>(flinkProperties.size());
            flinkProperties.forEach((k, v) -> map.put((String) k, (String) v));
            return ParameterTool.fromMap(map);
        }
    }

    private static KafkaSource<String> createKafkaSource(ParameterTool applicationProperties) {
        String bootstrapServers = applicationProperties.get(MSKBOOTSTRAP_SERVERS_KEY);
        Preconditions.checkNotNull(bootstrapServers, MSKBOOTSTRAP_SERVERS_KEY + " configuration missing");

        KafkaSourceBuilder<String> builder = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(applicationProperties.get(KAFKA_SOURCE_TOPIC_KEY, DEFAULT_SOURCE_TOPIC))
                .setGroupId(applicationProperties.get(KAFKA_CONSUMER_GROUP_ID_KEY, DEFAULT_CONSUMER_GROUP))
                .setStartingOffsets(OffsetsInitializer.earliest()) // Used when the application starts with no state
                .setValueOnlyDeserializer(new SimpleStringSchema());

        builder.setProperty("security.protocol", "SSL");
        configureConnectorPropsWithConfigProviders(builder, applicationProperties);

        return builder.build();
    }

    private static void configureConnectorPropsWithConfigProviders(KafkaSourceBuilder<String> builder, ParameterTool appProperties) {
        // see https://github.com/aws-samples/msk-config-providers

        // define names of config providers:
        builder.setProperty("config.providers", "secretsmanager,s3import");

        // provide implementation classes for each provider:
        builder.setProperty("config.providers.secretsmanager.class", "com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider");
        builder.setProperty("config.providers.s3import.class", "com.amazonaws.kafka.config.providers.S3ImportConfigProvider");

        String region = appProperties.get(S3_BUCKET_REGION_KEY);
        String keystoreS3Bucket = appProperties.get(KEYSTORE_S3_BUCKET_KEY);
        String keystoreS3Path = appProperties.get(KEYSTORE_S3_PATH_KEY);
        String truststoreS3Bucket = appProperties.get(TRUSTSTORE_S3_BUCKET_KEY);
        String truststoreS3Path = appProperties.get(TRUSTSTORE_S3_PATH_KEY);
        String keystorePassSecret = appProperties.get(KEYSTORE_PASS_SECRET_KEY);
        String keystorePassSecretField = appProperties.get(KEYSTORE_PASS_SECRET_FIELD_KEY);

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
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);

        KafkaSource<String> source = createKafkaSource(applicationProperties);
        DataStream<String> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka source");

        input.print(); // Never print/log every record in a production application

        env.execute("Flink Kafka Source from MSK using mTLS");
    }
}
