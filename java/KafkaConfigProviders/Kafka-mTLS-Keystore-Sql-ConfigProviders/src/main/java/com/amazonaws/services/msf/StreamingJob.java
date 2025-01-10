package com.amazonaws.services.msf;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import static org.apache.flink.table.api.Expressions.$;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;

public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String DEFAULT_SOURCE_TOPIC = "source";
    private static final String DEFAULT_SINK_TOPIC = "destination";
    private static final String DEFAULT_CONSUMER_GROUP = "flink-app";
    private static final String KAFKA_SOURCE_TOPIC_KEY = "topic";
    private static final String KAFKA_SINK_TOPIC_KEY = "topic";
    private static final String SOURCE_MSKBOOTSTRAP_SERVERS_KEY = "bootstrap.servers";
    private static final String SINK_MSKBOOTSTRAP_SERVERS_KEY = "bootstrap.servers";
    private static final String KAFKA_CONSUMER_GROUP_ID_KEY = "group.id";
    private static final String S3_BUCKET_REGION_KEY = "bucket.region";
    private static final String KEYSTORE_S3_BUCKET_KEY = "keystore.bucket";
    private static final String KEYSTORE_S3_PATH_KEY = "keystore.path";
    private static final String KEYSTORE_PASS_SECRET_KEY = "keystore.secret";
    private static final String KEYSTORE_PASS_SECRET_FIELD_KEY = "keystore.secret.field";

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

    public static void main(String[] args) throws Exception {
        // Set up the stream table environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // Load the application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties inputProperties = applicationProperties.get("Input0");

        // Create Kafka source table
        tableEnv.executeSql(
            "CREATE TABLE sourceTable (" +
            "  `user` STRING, " +
            "  `message` STRING, " +
            "  `ts` TIMESTAMP(3) " +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = '" + inputProperties.getProperty(KAFKA_SOURCE_TOPIC_KEY, DEFAULT_SOURCE_TOPIC) + "'," +
            "  'properties.bootstrap.servers' = '" + inputProperties.getProperty(SOURCE_MSKBOOTSTRAP_SERVERS_KEY) + "'," +
            "  'properties.group.id' = '" + inputProperties.getProperty(KAFKA_CONSUMER_GROUP_ID_KEY, DEFAULT_CONSUMER_GROUP) + "'," +
            "  'properties.security.protocol' = 'SSL'," +
            "  'properties.config.providers' = 'secretsmanager,s3import'," +
            "  'properties.config.providers.secretsmanager.class' = 'com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider'," +
            "  'properties.config.providers.s3import.class' = 'com.amazonaws.kafka.config.providers.S3ImportConfigProvider'," +
            "  'properties.config.providers.s3import.param.region' = '" + inputProperties.getProperty(S3_BUCKET_REGION_KEY) + "'," +
            "  'properties.ssl.keystore.type' = 'PKCS12'," +
            "  'properties.ssl.keystore.location' = '${s3import:" + inputProperties.getProperty(S3_BUCKET_REGION_KEY) + ":" + inputProperties.getProperty(KEYSTORE_S3_BUCKET_KEY) + "/" + inputProperties.getProperty(KEYSTORE_S3_PATH_KEY) + "}'," +
            "  'properties.ssl.keystore.password' = '${secretsmanager:" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
            "  'properties.ssl.key.password' = '${secretsmanager:" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
            "  'scan.startup.mode' = 'earliest-offset'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );

        Properties outputProperties = applicationProperties.get("Output0");

        // Create Kafka sink table
        tableEnv.executeSql(
            "CREATE TABLE sinkTable (" +
            "  `user` STRING, " +
            "  `message` STRING, " +
            "  `ts` TIMESTAMP(3) " +
            ") WITH (" +
            "  'connector' = 'kafka'," +
            "  'topic' = '" + outputProperties.getProperty(KAFKA_SINK_TOPIC_KEY, DEFAULT_SINK_TOPIC) + "'," +
            "  'properties.bootstrap.servers' = '" + outputProperties.getProperty(SINK_MSKBOOTSTRAP_SERVERS_KEY) + "'," +
            "  'properties.group.id' = '" + outputProperties.getProperty(KAFKA_CONSUMER_GROUP_ID_KEY, DEFAULT_CONSUMER_GROUP) + "'," +
            "  'properties.security.protocol' = 'SSL'," +
            "  'properties.config.providers' = 'secretsmanager,s3import'," +
            "  'properties.config.providers.secretsmanager.class' = 'com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider'," +
            "  'properties.config.providers.s3import.class' = 'com.amazonaws.kafka.config.providers.S3ImportConfigProvider'," +
            "  'properties.config.providers.s3import.param.region' = '" + outputProperties.getProperty(S3_BUCKET_REGION_KEY) + "'," +
            "  'properties.ssl.keystore.type' = 'PKCS12'," +
            "  'properties.ssl.keystore.location' = '${s3import:" + outputProperties.getProperty(S3_BUCKET_REGION_KEY) + ":" + outputProperties.getProperty(KEYSTORE_S3_BUCKET_KEY) + "/" + outputProperties.getProperty(KEYSTORE_S3_PATH_KEY) + "}'," +
            "  'properties.ssl.keystore.password' = '${secretsmanager:" + outputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + outputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
            "  'properties.ssl.key.password' = '${secretsmanager:" + outputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + outputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
            "  'format' = 'json'," +
            "  'json.ignore-parse-errors' = 'true'" +
            ")"
        );

        Table resultTable = tableEnv.from("sourceTable").select($("user"), $("message"), $("ts"));

        // Emit the Table API result Table to sink table
        TableResult result = resultTable.insertInto("sinkTable").execute();
        LOG.info("Job status through TableResult: ", result.getJobClient().get().getJobStatus());
    }
}
