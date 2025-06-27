package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.bridge.java.StreamStatementSet;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class FlinkCDCSqlServer2JdbcJob {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkCDCSqlServer2JdbcJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static final int DEFAULT_CDC_DB_PORT = 1433;

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
                    FlinkCDCSqlServer2JdbcJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, EnvironmentSettings.newInstance().build());

        final Map<String, Properties> applicationProperties = loadApplicationProperties(env);
        LOG.warn("Application properties: {}", applicationProperties);

        // Enable checkpoints and set parallelism when running locally
        // On Managed Flink, checkpoints and application parallelism are managed by the service and controlled by the application configuration
        if (isLocal(env)) {
            env.setParallelism(1); // Ms SQL Server Flink CDC is single-threaded
            env.enableCheckpointing(30000);
        }


        // Create CDC source table
        Properties cdcSourceProperties = applicationProperties.get("CDCSource");
        tableEnv.executeSql("CREATE TABLE Customers (" +
                "  CustomerID INT," +
                "  FirstName STRING," +
                "  MiddleInitial STRING," +
                "  LastName STRING," +
                "  mail STRING," +
                // Some additional metadata columns for demonstration purposes
                "  `_change_processed_at` AS PROCTIME()," + // The time when Flink is processing this record
                "  `_source_updated_at` TIMESTAMP_LTZ(3) METADATA FROM 'op_ts' VIRTUAL," + // The time when the operation was executed on the db
                "  `_table_name` STRING METADATA FROM 'table_name' VIRTUAL," + // Name of the table in the source db
                "  `_schema_name` STRING METADATA FROM 'schema_name' VIRTUAL, " + // Name of the schema in the source db
                "  `_db_name` STRING METADATA FROM 'database_name' VIRTUAL," + // name of the database
                "  PRIMARY KEY(CustomerID) NOT ENFORCED" +
                ") WITH (" +
                "  'connector' = 'sqlserver-cdc'," +
                "  'hostname' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("hostname"), "missing CDC source hostname") + "'," +
                "  'port' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("port", Integer.toString(DEFAULT_CDC_DB_PORT)), "missing CDC source port") + "'," +
                "  'username' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("username"), "missing CDC source username") + "'," +
                // For simplicity, we are passing the db password as a runtime configuration unencrypted. This should be avoided in production
                "  'password' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("password"), "missing CDC source password") + "'," +
                "  'database-name' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("database.name"), "missing CDC source database name") + "'," +
                "  'table-name' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("table.name"), "missing CDC source table name") + "'" +
                ")");


        // Create a JDBC sink table
        // Note that the definition of the table is agnostic to the actual destination database (e.g. MySQL or PostgreSQL)
        Properties jdbcSinkProperties = applicationProperties.get("JdbcSink");
        tableEnv.executeSql("CREATE TABLE DestinationTable (" +
                "  customer_id INT," +
                "  first_name STRING," +
                "  middle_initial STRING," +
                "  last_name STRING," +
                "  email STRING," +
                "  _source_updated_at TIMESTAMP(3)," +
                "  _change_processed_at TIMESTAMP(3)," +
                "  PRIMARY KEY(customer_id) NOT ENFORCED" +
                ") WITH (" +
                "  'connector' = 'jdbc'," +
                "  'url' = '" + Preconditions.checkNotNull(jdbcSinkProperties.getProperty("url"), "missing destination database JDBC URL") + "'," +
                "  'table-name' = '" + Preconditions.checkNotNull(jdbcSinkProperties.getProperty("table.name"), "missing destination database table name") + "'," +
                "  'username' = '" + Preconditions.checkNotNull(jdbcSinkProperties.getProperty("username"), "missing destination database username") + "'," +
                "  'password' = '" + Preconditions.checkNotNull(jdbcSinkProperties.getProperty("password"), "missing destination database password") + "'" +
                ")");

        // When running locally we add a secondary sink to print the output to the console.
        // When the job is running on Managed Flink any output to console is not visible and may cause overhead.
        // It is recommended not to print any output to the console when running the application on Managed Flink.
        if( isLocal(env)) {
            tableEnv.executeSql("CREATE TABLE PrintSinkTable (" +
                    "  CustomerID INT," +
                    "  FirstName STRING," +
                    "  MiddleInitial STRING," +
                    "  LastName STRING," +
                    "  mail STRING," +
                    "  `_change_processed_at` TIMESTAMP_LTZ(3)," +
                    "  `_source_updated_at` TIMESTAMP_LTZ(3)," +
                    "  `_table_name` STRING," +
                    "  `_schema_name` STRING," +
                    "  `_db_name` STRING," +
                    "  PRIMARY KEY(CustomerID) NOT ENFORCED" +
                    ") WITH (" +
                    "  'connector' = 'print'" +
                    ")");
        }

        // Note that we use a statement set to add the two "INSERT INTO..." statements.
        // When tableEnv.executeSQL(...) is used with INSERT INTO on a job running in Application mode, like on Managed Flink,
        // the first statement triggers the job execution, and any code which follows is ignored.
        StreamStatementSet statementSet = tableEnv.createStatementSet();
        statementSet.addInsertSql("INSERT INTO DestinationTable (" +
                "customer_id, " +
                "first_name, " +
                "middle_initial, " +
                "last_name, " +
                "email, " +
                "_source_updated_at, " +
                "_change_processed_at" +
                ") SELECT " +
                "CustomerID, " +
                "FirstName, " +
                "MiddleInitial, " +
                "LastName, " +
                "mail, " +
                "`_source_updated_at`, " +
                "`_change_processed_at` " +
                "FROM Customers");
        if( isLocal(env)) {
            statementSet.addInsertSql("INSERT INTO PrintSinkTable SELECT * FROM Customers");
        }


        // Execute the two INSERT INTO statements
        statementSet.execute();
    }
}
