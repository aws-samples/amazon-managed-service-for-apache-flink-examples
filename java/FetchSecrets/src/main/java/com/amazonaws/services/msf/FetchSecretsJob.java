package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

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

    /**
     * Fetch the secret from Secrets Manager.
     * In the case of MSK SASL/SCRAM credentials, the secret is a JSON object with the keys `username` and `password`.
     *
     * @param authProperties The properties containing the `secret.name`
     * @return A Tuple2 containing the username and password
     */
    private static Tuple2<String, String> fetchCredentialsFromSecretsManager(Properties authProperties) {
        String secretName = Preconditions.checkNotNull(authProperties.getProperty("secret.name"), "Missing secret name");

        try (SecretsManagerClient client = SecretsManagerClient.create()) {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            String secretString = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretJson = mapper.readTree(secretString);

            String username = secretJson.get("username").asText();
            String password = secretJson.get("password").asText();

            LOG.info("Successfully fetched secrets - username: {}, password: {}", username, "****");
            return new Tuple2<>(username, password);

        } catch (Exception e) {
            throw new RuntimeException("Unable to fetch credentials from Secrets Manager", e);
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

    private static KafkaSink<StockPrice> createKafkaSink(Properties outputProperties, Tuple2<String, String> saslScramCredentials) {
        Properties kafkaProducerConfig = new Properties(outputProperties);

        // Add to the kafka producer properties the parameters to enable SASL/SCRAM auth
        LOG.info("Setting up Kafka SASL/SCRAM authentication");
        kafkaProducerConfig.setProperty("security.protocol", "SASL_SSL");
        kafkaProducerConfig.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        String jassConfig = String.format(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                saslScramCredentials.f0,
                saslScramCredentials.f1);
        kafkaProducerConfig.setProperty("sasl.jaas.config", jassConfig);

        String topic = outputProperties.getProperty("topic", DEFAULT_TOPIC);

        KafkaRecordSerializationSchema<StockPrice> recordSerializationSchema =
                KafkaRecordSerializationSchema.<StockPrice>builder()
                        .setTopic(topic)
                        .setKeySerializationSchema(stock -> stock.getSymbol().getBytes())
                        .setValueSerializationSchema(new JsonSerializationSchema<>())
                        .build();

        return KafkaSink.<StockPrice>builder()
                .setBootstrapServers(outputProperties.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(kafkaProducerConfig)
                .setRecordSerializer(recordSerializationSchema)
                .build();
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        Properties authProperties = applicationProperties.getOrDefault("AuthProperties", new Properties());
        Properties dataGenProperties = applicationProperties.getOrDefault("DataGen", new Properties());
        Properties outputProperties = applicationProperties.get("Output0");

        // Fetch the credentials from Secrets Manager
        // Note that the credentials are fetched only once, when the job start
        Tuple2<String, String> credentials = fetchCredentialsFromSecretsManager(authProperties);

        // Create the data generator
        DataGeneratorSource<StockPrice> source = createDataGeneratorSource(dataGenProperties);
        DataStream<StockPrice> stockPriceStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Stock Price Generator"
        );

        // Create the Kafka Sink, passing the credentials
        KafkaSink<StockPrice> sink = createKafkaSink(outputProperties, credentials);
        stockPriceStream.sinkTo(sink);

        if (isLocal(env)) {
            stockPriceStream.print();
        }

        env.execute("Stock Price to Kafka Job");
    }
}
