package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.domain.User;
import com.amazonaws.services.msf.domain.UserGeneratorFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSink;
import org.apache.flink.connector.jdbc.core.datastream.sink.JdbcSinkBuilder;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;

/**
 * A Flink application that generates random user data using DataGeneratorSource
 * and writes it to a PostgreSQL database using the JDBC connector.
 */
public class JdbcSinkJob {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcSinkJob.class);

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
                    JdbcSinkJob.class.getClassLoader()
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

    private static JdbcSink<User> createJdbcSink(Properties sinkProperties) {


        return JdbcSink.builder()
                .buildAtLeastOnce();
    }


//    /**
//     * Create a JDBC Sink for PostgreSQL using the non-deprecated API
//     *
//     * @param jdbcProperties Properties from the "JdbcSink" property group
//     * @return an instance of SinkFunction for User objects
//     */
//    private static SinkFunction<User> createJdbcSink(Properties jdbcProperties) {
//        String jdbcUrl = Preconditions.checkNotNull(
//                jdbcProperties.getProperty("url"),
//                "JDBC URL is required"
//        );
//        String username = Preconditions.checkNotNull(
//                jdbcProperties.getProperty("username"),
//                "JDBC username is required"
//        );
//        String password = Preconditions.checkNotNull(
//                jdbcProperties.getProperty("password"),
//                "JDBC password is required"
//        );
//        String tableName = jdbcProperties.getProperty("table.name", "users");
//
//        // SQL statement for inserting user data with all fields
//        String insertSQL = String.format(
//                "INSERT INTO %s (user_id, first_name, last_name, email, phone_number, address, city, country, job_title, company, date_of_birth, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
//                tableName
//        );
//
//        // JDBC statement builder
//        JdbcStatementBuilder<User> statementBuilder = new JdbcStatementBuilder<User>() {
//            @Override
//            public void accept(PreparedStatement preparedStatement, User user) throws SQLException {
//                preparedStatement.setInt(1, user.getUserId());
//                preparedStatement.setString(2, user.getFirstName());
//                preparedStatement.setString(3, user.getLastName());
//                preparedStatement.setString(4, user.getEmail());
//                preparedStatement.setString(5, user.getPhoneNumber());
//                preparedStatement.setString(6, user.getAddress());
//                preparedStatement.setString(7, user.getCity());
//                preparedStatement.setString(8, user.getCountry());
//                preparedStatement.setString(9, user.getJobTitle());
//                preparedStatement.setString(10, user.getCompany());
//
//                // Parse the date of birth and convert to SQL Date
//                if (user.getDateOfBirth() != null && !user.getDateOfBirth().isEmpty()) {
//                    java.time.LocalDate birthDate = java.time.LocalDate.parse(user.getDateOfBirth(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
//                    preparedStatement.setDate(11, java.sql.Date.valueOf(birthDate));
//                } else {
//                    preparedStatement.setDate(11, null);
//                }
//
//                // Parse the ISO timestamp and convert to SQL Timestamp
//                LocalDateTime dateTime = LocalDateTime.parse(user.getCreatedAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
//                preparedStatement.setTimestamp(12, Timestamp.valueOf(dateTime));
//            }
//        };
//
//        // JDBC connection options
//        JdbcConnectionOptions connectionOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
//                .withUrl(jdbcUrl)
//                .withDriverName("org.postgresql.Driver")
//                .withUsername(username)
//                .withPassword(password)
//                .build();
//
//        // JDBC execution options
//        JdbcExecutionOptions executionOptions = JdbcExecutionOptions.builder()
//                .withBatchSize(1000)
//                .withBatchIntervalMs(200)
//                .withMaxRetries(5)
//                .build();
//
//        // Use the non-deprecated JdbcSink from org.apache.flink.connector.jdbc.sink
//        return JdbcSink.<User>builder()
//                .setJdbcConnectionOptions(connectionOptions)
//                .setJdbcExecutionOptions(executionOptions)
//                .setJdbcStatementBuilder(statementBuilder)
//                .setSql(insertSQL)
//                .build();
//    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Allows Flink to reuse objects across forwarded operators, as opposed to do a deep copy
        // (this is safe because record objects are never mutated or passed by reference)
        env.getConfig().enableObjectReuse();

        LOG.info("Starting Flink JDBC Sink Job");

        // Load application properties
        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.info("Application properties: {}", applicationProperties);

        // Create a DataGeneratorSource that generates User objects
        DataGeneratorSource<User> source = createDataGeneratorSource(
                applicationProperties.get("DataGen"),
                new UserGeneratorFunction(),
                TypeInformation.of(User.class)
        );

        // Create the data stream from the source
        DataStream<User> userStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "User Data Generator"
        ).uid("user-data-generator");

        // Check if JDBC sink is configured
        Properties jdbcProperties = applicationProperties.get("JdbcSink");
        if (jdbcProperties == null) {
            throw new IllegalArgumentException(
                    "JdbcSink configuration is required. Please provide 'JdbcSink' configuration group.");
        }

        // Create JDBC sink
        JdbcSink<User> jdbcSink = createJdbcSink(jdbcProperties);
        userStream.sinkTo(jdbcSink).uid("jdbc-sink").name("PostgreSQL Sink");

        // Add print sink for local testing
        if (isLocal(env)) {
            userStream.print().uid("print-sink").name("Print Sink");
            LOG.info("Print sink configured for local testing");
        }

        LOG.info("JDBC sink configured");

        // Execute the job
        env.execute("Flink JDBC Sink Job");
    }
}
