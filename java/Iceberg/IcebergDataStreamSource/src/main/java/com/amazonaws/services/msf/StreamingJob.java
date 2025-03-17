package com.amazonaws.services.msf;

import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Preconditions;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.AvroSchemaUtils;
import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.CatalogLoader;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.source.FlinkSource;
import org.apache.iceberg.flink.source.IcebergSource;
import org.apache.iceberg.flink.source.StreamingStartingStrategy;
import org.apache.iceberg.flink.source.assigner.SimpleSplitAssignerFactory;
import org.apache.iceberg.flink.source.reader.AvroGenericRecordReaderFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;


public class StreamingJob {
    private final static Logger LOG = LoggerFactory.getLogger(StreamingJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

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
                    Objects.requireNonNull(StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        Map<String, Properties> applicationProperties = loadApplicationProperties(env);


        // Get AVRO Schema from the definition bundled with the application
        // Note that the application must "knows" the AVRO schema upfront, i.e. the schema must be either embedded
        // with the application or fetched at start time.
        // If the schema of the records received by the source changes, all changes must be FORWARD compatible.
        // This way, the application will be able to write the data with the new schema into the old schema, but schema
        // changes are not propagated to the Iceberg table.
        Schema avroSchema = AvroSchemaUtils.loadSchema();


        // Local dev specific settings
        if (isLocal(env)) {
            // We are disabling operator chaining when running locally, to allow observing every single operator in the
            // Flink UI, for demonstration purposes.
            // Disabling operator chaining can harm performance and is not recommended.
            env.disableOperatorChaining();

            // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
            env.enableCheckpointing(60000);
            env.setParallelism(2);
        }

        Properties icebergProperties = applicationProperties.get("Iceberg");

        String s3BucketPrefix = Preconditions.checkNotNull(icebergProperties.getProperty("bucket.prefix"), "Iceberg S3 bucket prefix not defined");


        // Catalog properties for using Glue Data Catalog
        Map<String, String> catalogProperties = new HashMap<>();
        catalogProperties.put("type", "iceberg");
        catalogProperties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        catalogProperties.put("warehouse", s3BucketPrefix);

        CatalogLoader glueCatalogLoader =
                CatalogLoader.custom(
                        "glue",
                        catalogProperties,
                        new Configuration(),
                        "org.apache.iceberg.aws.glue.GlueCatalog");

        String DEFAULT_GLUE_DB = "iceberg";
        String DEFAULT_ICEBERG_TABLE_NAME = "prices_iceberg";

        String glueDatabase = icebergProperties.getProperty("catalog.db", DEFAULT_GLUE_DB);
        String glueTable = icebergProperties.getProperty("catalog.table", DEFAULT_ICEBERG_TABLE_NAME);
        TableIdentifier inputTable = TableIdentifier.of(glueDatabase, glueTable);

        TableLoader tableLoader = TableLoader.fromCatalog(glueCatalogLoader, inputTable);
        Table table;
        try(TableLoader loader = tableLoader) {
            loader.open();
            table = loader.loadTable();
        }

        AvroGenericRecordReaderFunction readerFunction = AvroGenericRecordReaderFunction.fromTable(table);

        IcebergSource<GenericRecord> source =
                IcebergSource.<GenericRecord>builder()
                        .tableLoader(tableLoader)
                        .readerFunction(readerFunction)
                        .assignerFactory(new SimpleSplitAssignerFactory())
                        .monitorInterval(Duration.ofSeconds(60))
                        .streaming(true)
        .build();


        DataStreamSource<GenericRecord> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(),
                "Iceberg Source as Avro GenericRecord", new GenericRecordAvroTypeInfo(avroSchema));


        stream.print();

        env.execute("Flink DataStream Source");
    }
}