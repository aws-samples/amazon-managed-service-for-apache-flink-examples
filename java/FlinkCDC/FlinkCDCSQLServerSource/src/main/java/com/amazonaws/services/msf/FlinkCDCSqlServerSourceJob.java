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

public class FlinkCDCSqlServerSourceJob {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkCDCSqlServerSourceJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static final int DEFAULT_CDC_DB_PORT = 1433;
    private static final String DEFAULT_CDC_DATABASE_NAME = "SampleDataPlatform";
    private static final String DEFAULT_CDC_TABLE_NAME = "dbo.Customers";
    private static final String DEFAULT_DDB_TABLE_NAME = "Customers";

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
                    FlinkCDCSqlServerSourceJob.class.getClassLoader()
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
                "  `_ingestion_ts` AS PROCTIME()," + // The time when Flink is processing this record
                "  `_operation_ts` TIMESTAMP_LTZ(3) METADATA FROM 'op_ts' VIRTUAL," + // The time when the operation was executed on the db
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
                "  'database-name' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("database.name", DEFAULT_CDC_DATABASE_NAME), "missing CDC source database name") + "'," +
                "  'table-name' = '" + Preconditions.checkNotNull(cdcSourceProperties.getProperty("table.name", DEFAULT_CDC_TABLE_NAME), "missing CDC source table name") + "'" +
                ")");


        // Create a DynamoDB sink table
        Properties dynamoDBProperties = applicationProperties.get("DynamoDBSink");
        tableEnv.executeSql("CREATE TABLE DDBSinkTable (" +
                "  CustomerID INT," +
                "  FirstName STRING," +
                "  MiddleInitial STRING," +
                "  LastName STRING," +
                "  mail STRING," +
                "  `_ingestion_ts` TIMESTAMP_LTZ(3)," +
                "  `_operation_ts` TIMESTAMP_LTZ(3)," +
                "  `_table_name` STRING," +
                "  `_schema_name` STRING," +
                "  `_db_name` STRING" +
                //               "  PRIMARY KEY(CustomerID) NOT ENFORCED" +
                ") PARTITIONED BY (`CustomerID`) " +
                "WITH (" +
                "  'connector' = 'dynamodb'," +
                "  'table-name' = '" + Preconditions.checkNotNull(dynamoDBProperties.getProperty("table.name", DEFAULT_DDB_TABLE_NAME), "missing DynamoDB table name") + "'," +
                "  'aws.region' = '" + Preconditions.checkNotNull(dynamoDBProperties.getProperty("aws.region"), "missing AWS region") + "'" +
                ")");

        // While developing locally, you can comment the sink table to DynamoDB and uncomment the following table to print records to the console
        // When the job is running on Managed Flink any output to console is not visible
        tableEnv.executeSql("CREATE TABLE PrintSinkTable (" +
                "  CustomerID INT," +
                "  FirstName STRING," +
                "  MiddleInitial STRING," +
                "  LastName STRING," +
                "  mail STRING," +
                "  `_ingestion_ts` TIMESTAMP_LTZ(3)," +
                "  `_operation_ts` TIMESTAMP_LTZ(3)," +
                "  `_table_name` STRING," +
                "  `_schema_name` STRING," +
                "  `_db_name` STRING," +
                "  PRIMARY KEY(CustomerID) NOT ENFORCED" +
                ") WITH (" +
                "  'connector' = 'print'" +
                ")");


        StreamStatementSet statementSet = tableEnv.createStatementSet();
        statementSet.addInsertSql("INSERT INTO DDBSinkTable SELECT * FROM Customers");
        statementSet.addInsertSql("INSERT INTO PrintSinkTable SELECT * FROM Customers");

        // Execute the two INSERT INTO statements
        statementSet.execute();
    }
}
