package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.aws.config.AWSConfigConstants;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.*;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.*;
import org.apache.iceberg.flink.sink.AvroGenericRecordToRowDataMapper;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.flink.util.FlinkCompatibilityUtil;
import java.io.File;
import java.io.IOException;
import java.util.*;


public class StreamingJob {
    private static final String DEFAULT_SOURCE_STREAM = "kds-stream-name";
    private static final String DEFAULT_AWS_REGION = "eu-west-1";
    private static final String DEFAULT_ICEBERG_S3_BUCKET = "s3://<s3Bucket>/warehouse/iceberg";
    private static final String DEFAULT_GLUE_DB = "iceberg";
    private static final String DEFAULT_ICEBERG_TABLE_NAME = "trade_iceberg";
    private static final String DEFAULT_ICEBERG_SORT_ORDER_FIELD = "accountNr";
    private static final String DEFAULT_ICEBERG_PARTITION_FIELDS = "symbol,accountNr";
    private static final String DEFAULT_ICEBERG_OPERATION = "append";
    private static final String DEFAULT_ICEBERG_UPSERT_FIELDS = "accountNr,symbol";

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

    private static FlinkKinesisConsumer createKinesisSource(
            ParameterTool applicationProperties) {

        // Properties for Amazon Kinesis Data Streams Source, we need to specify from where we want to consume the data.
        // STREAM_INITIAL_POSITION: LATEST: consume messages that have arrived from the moment application has been deployed
        // STREAM_INITIAL_POSITION: TRIM_HORIZON: consume messages starting from first available in the Kinesis Stream
        Properties kinesisConsumerConfig = new Properties();
        kinesisConsumerConfig.put(AWSConfigConstants.AWS_REGION, applicationProperties.get("kinesis.region",DEFAULT_AWS_REGION));
        kinesisConsumerConfig.put(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");

        // If EFO consumer is needed, uncomment the following block.
        /*
        kinesisConsumerConfig.put(ConsumerConfigConstants.RECORD_PUBLISHER_TYPE,
                ConsumerConfigConstants.RecordPublisherType.EFO.name());
        kinesisConsumerConfig.put(ConsumerConfigConstants.EFO_CONSUMER_NAME,"my-efo-consumer");
         */

        return new FlinkKinesisConsumer<>(applicationProperties.get("kinesis.source",DEFAULT_SOURCE_STREAM), new SimpleStringSchema(), kinesisConsumerConfig);
    }

    // Used to generate the PartitionSpec, if when creating table, you want it to be partitioned.
    // If you are doing Upserts in your Iceberg Table, your Equality Fields must be the same as the fields used for Partitioning.
    private static PartitionSpec getPartitionSpec(org.apache.iceberg.Schema icebergSchema, List<String> partitionFieldsList) {
        PartitionSpec.Builder partitionBuilder = PartitionSpec.builderFor(icebergSchema);
        for (int i = 0; i < partitionFieldsList.size(); i++) {
            partitionBuilder = partitionBuilder.identity(partitionFieldsList.get(i));
        }
        PartitionSpec partitionSpec = partitionBuilder.build();
        return partitionSpec;
    }

    // If Iceberg Table has not been previously created, we will create it using the Partition Fields specified in the Properties, as well as add a Sort Field to improve query performance
    private static void createTable(Catalog catalog, TableIdentifier outputTable, org.apache.iceberg.Schema icebergSchema, PartitionSpec partitionSpec, String sortField) {
        //If table has been previously created, we do not do any operation or modification
        if (!catalog.tableExists(outputTable)) {
            Table icebergTable = catalog.createTable(outputTable, icebergSchema, partitionSpec);
           // Modifying newly created iceberg table to have a sort field
            icebergTable.replaceSortOrder()
                    .asc(sortField,NullOrder.NULLS_LAST)
                    .commit();
            // The catalog.create table creates an Iceberg V1 table. If we want to perform upserts, we need to upgrade the table version to 2.
            TableOperations tableOperations = ((BaseTable) icebergTable).operations();
            TableMetadata appendTableMetadata = tableOperations.current();
            tableOperations.commit(appendTableMetadata, appendTableMetadata.upgradeToFormatVersion(2));
        }
    }

    // Iceberg Flink Sink Builder
    private static FlinkSink.Builder createIcebergSinkBuilder(ParameterTool applicationProperties,DataStream<GenericRecord> dataStream, Schema avroSchema) {
        //Converting Avro Schema to Iceberg Schema, this will be used for creating the table
        org.apache.iceberg.Schema icebergSchema = AvroSchemaUtil.toIceberg(avroSchema);
        //Catalog properties for using Glue Data Catalog
        Map<String, String> catalogProperties = new HashMap<>();
        catalogProperties.put("type", "iceberg");
        catalogProperties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        catalogProperties.put("warehouse", applicationProperties.get("iceberg.warehouse",DEFAULT_ICEBERG_S3_BUCKET));
        catalogProperties.put("impl", "org.apache.iceberg.aws.glue.GlueCatalog");
        //Loading Glue Data Catalog
        CatalogLoader glueCatalogLoader = CatalogLoader.custom(
                "glue",
                catalogProperties,
                new org.apache.hadoop.conf.Configuration(),
                "org.apache.iceberg.aws.glue.GlueCatalog");
        // Table Object that represents the table in the Glue Data Catalog
        TableIdentifier outputTable = TableIdentifier.of(
                applicationProperties.get("iceberg.db",DEFAULT_GLUE_DB),
                applicationProperties.get("iceberg.table",DEFAULT_ICEBERG_TABLE_NAME));
        // Load created Iceberg Catalog to perform table operations
        Catalog catalog = glueCatalogLoader.loadCatalog();
        // Get the partition fields from the Application properties
        String partitionFields = Preconditions.checkNotNull(applicationProperties.get("iceberg.partition.fields",DEFAULT_ICEBERG_PARTITION_FIELDS),"Iceberg Partition (yes/no) to  not defined");
        // We parse the string into an Array List
        List<String> partitionFieldList = Arrays.asList(partitionFields.split("\\s*,\\s*"));
        // Based on how many fields we want to partition, we create the Partition Spec
        PartitionSpec partitionSpec = getPartitionSpec(icebergSchema,partitionFieldList);
        // We create the Iceberg Table, using the Iceberg Catalog, Table Identifier, Schema parsed in Iceberg Schema Format and the partition spec
        createTable(catalog, outputTable, icebergSchema, partitionSpec,applicationProperties.get("iceberg.sort.field",DEFAULT_ICEBERG_SORT_ORDER_FIELD));
        // Once the table has been created in the job or before, we load it
        TableLoader tableLoader= TableLoader.fromCatalog(glueCatalogLoader, outputTable);
        //Get RowType Schema from Iceberg Schema
        RowType rowType = FlinkSchemaUtil.convert(icebergSchema);
        // Builder for Iceberg Flink Sink
        FlinkSink.Builder flinkSinkBuilder = FlinkSink.builderFor(dataStream,
                        AvroGenericRecordToRowDataMapper.forAvroSchema(avroSchema),
                        FlinkCompatibilityUtil.toTypeInfo(rowType))
                    .tableLoader(tableLoader);
        // In Iceberg you can perform Appends, Upserts and Overwrites.
        String icebergOperation = applicationProperties.get("iceberg.operation",DEFAULT_ICEBERG_OPERATION);
        // Flink Sink Builder for Upsert Operation
        if (icebergOperation.equals("upsert")) {
            //Get from Application Properties the fields for which we want to upsert by. Remember if the table is partitioned, we must include the partition fields
            String equalityFieldsString = Preconditions.checkNotNull(applicationProperties.get("iceberg.upsert.equality.fields",DEFAULT_ICEBERG_UPSERT_FIELDS),"Iceberg Equality Fields not defined");
            List<String> equalityFieldsListString = Arrays.asList(equalityFieldsString.split("[, ]+"));
            return flinkSinkBuilder
                    .equalityFieldColumns(equalityFieldsListString)
                    .upsert(true);
        }
        // Flink Sink Builder for Overwrite Operation

        else if (icebergOperation.equals("overwrite")) {
            return flinkSinkBuilder
                    .overwrite(true);
        }
        else {
            // Default to an Append Builder
            return flinkSinkBuilder;
        }
    }

    public static void main(String[] args) throws Exception {
        // Get Avro Schema from resources
        Schema avroSchema = new Schema.Parser().parse(new File("./src/main/resources/trade.avsc"));
        // Create Generic Record TypeInfo from schema.
        GenericRecordAvroTypeInfo avroTypeInfo = new GenericRecordAvroTypeInfo(avroSchema);
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final ParameterTool applicationProperties = loadApplicationParameters(args, env);

        // Local dev specific settings
        if (env instanceof LocalStreamEnvironment) {
            // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
            env.enableCheckpointing(10000);
            env.setParallelism(5);
        }

        // Verify that Checkpoint is enabled
        if (!env.getCheckpointConfig().isCheckpointingEnabled()) {
            throw new Exception("In Flink Iceberg Jobs you need to enable Checkpointing for committing the data, if not data will accumulate and you will get OutOfMemoryError and no data sent to Iceberg Table");
        }

        // Flink Kinesis Consumer
        FlinkKinesisConsumer<String> source = createKinesisSource(applicationProperties);

        DataStream<String> input = env.addSource(source, "Kinesis source");

        // JsonString to Generic Record
        DataStream<GenericRecord> genericRecordDataStream = input
                .map(new JsonStringToGenericRecordMapFunction())
                .returns(avroTypeInfo);

        // Flink Sink Builder
        FlinkSink.Builder icebergSink = createIcebergSinkBuilder(applicationProperties,genericRecordDataStream,avroSchema);

        // Sink to Iceberg Table
        icebergSink.append();

        env.execute("Flink DataStream Sink");
    }
}
