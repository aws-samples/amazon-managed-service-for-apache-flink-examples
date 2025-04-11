package com.amazonaws.services.msf.iceberg;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.util.Preconditions;

import org.apache.avro.generic.GenericRecord;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.CatalogLoader;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.AvroGenericRecordToRowDataMapper;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.flink.util.FlinkCompatibilityUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Wraps the code to initialize an Iceberg sink that uses Glue Data Catalog as catalog
 */
public class IcebergSinkBuilder {
    private static final String DEFAULT_GLUE_DB = "default";
    private static final String DEFAULT_ICEBERG_TABLE_NAME = "prices_iceberg";
    private static final String DEFAULT_ICEBERG_PARTITION_FIELDS = "symbol";
    private static final String DEFAULT_ICEBERG_OPERATION = "upsert";
    private static final String DEFAULT_ICEBERG_UPSERT_FIELDS = "symbol";


    /**
     * If Iceberg Table has not been previously created, we will create it using the Partition Fields specified in the
     * Properties, as well as add a Sort Field to improve query performance
     */
    private static void createTable(Catalog catalog, TableIdentifier outputTable, org.apache.iceberg.Schema icebergSchema, PartitionSpec partitionSpec) {
        // If table has been previously created, we do not do any operation or modification
        if (!catalog.tableExists(outputTable)) {
            Table icebergTable = catalog.createTable(outputTable, icebergSchema, partitionSpec);
            // The catalog.create table creates an Iceberg V1 table. If we want to perform upserts, we need to upgrade the table version to 2.
            TableOperations tableOperations = ((BaseTable) icebergTable).operations();
            TableMetadata appendTableMetadata = tableOperations.current();
            tableOperations.commit(appendTableMetadata, appendTableMetadata.upgradeToFormatVersion(2));
        }
    }

    /**
     * Generate the PartitionSpec, if when creating table, you want it to be partitioned.
     * If you are doing Upserts in your Iceberg Table, your Equality Fields must be the same as the fields used for Partitioning.
     */
    private static PartitionSpec getPartitionSpec(org.apache.iceberg.Schema icebergSchema, List<String> partitionFieldsList) {
        PartitionSpec.Builder partitionBuilder = PartitionSpec.builderFor(icebergSchema);
        for (String s : partitionFieldsList) {
            partitionBuilder = partitionBuilder.identity(s);
        }
        return partitionBuilder.build();
    }

    // Iceberg Flink Sink Builder
    public static FlinkSink.Builder createBuilder(Properties icebergProperties, DataStream<GenericRecord> dataStream, org.apache.avro.Schema avroSchema) {
        // Retrieve configuration from application parameters
        String s3BucketPrefix = Preconditions.checkNotNull(icebergProperties.getProperty("bucket.prefix"), "Iceberg S3 bucket prefix not defined");

        String glueDatabase = icebergProperties.getProperty("catalog.db", DEFAULT_GLUE_DB);
        String glueTable = icebergProperties.getProperty("catalog.table", DEFAULT_ICEBERG_TABLE_NAME);

        String partitionFields = icebergProperties.getProperty("partition.fields", DEFAULT_ICEBERG_PARTITION_FIELDS);
        List<String> partitionFieldList = Arrays.asList(partitionFields.split("\\s*,\\s*"));

        // Iceberg you can perform Appends, Upserts and Overwrites.
        String icebergOperation = icebergProperties.getProperty("operation", DEFAULT_ICEBERG_OPERATION);
        Preconditions.checkArgument(icebergOperation.equals("append") || icebergOperation.equals("upsert") || icebergOperation.equals("overwrite"), "Invalid Iceberg Operation");

        // If operation is upsert, we need to specify the fields that will be used for equality in the upsert operation
        // If the table is partitioned, we must include the partition fields
        // This is a comma-separated list of fields
        String upsertEqualityFields = icebergProperties.getProperty("upsert.equality.fields", DEFAULT_ICEBERG_UPSERT_FIELDS);
        List<String> equalityFieldsList = Arrays.asList(upsertEqualityFields.split("[, ]+"));


        // Convert Avro Schema to Iceberg Schema, this will be used for creating the table
        org.apache.iceberg.Schema icebergSchema = AvroSchemaUtil.toIceberg(avroSchema);
        // Avro Generic Record to Row Data Mapper
        MapFunction<GenericRecord, RowData> avroGenericRecordToRowDataMapper = AvroGenericRecordToRowDataMapper.forAvroSchema(avroSchema);


        // Catalog properties for using Glue Data Catalog
        Map<String, String> catalogProperties = new HashMap<>();
        catalogProperties.put("type", "iceberg");
        catalogProperties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        catalogProperties.put("warehouse", s3BucketPrefix);

        // Load Glue Data Catalog
        CatalogLoader glueCatalogLoader = CatalogLoader.custom(
                "glue",
                catalogProperties,
                new org.apache.hadoop.conf.Configuration(),
                "org.apache.iceberg.aws.glue.GlueCatalog");
        // Table Object that represents the table in the Glue Data Catalog
        TableIdentifier outputTable = TableIdentifier.of(glueDatabase, glueTable);
        // Load created Iceberg Catalog to perform table operations
        Catalog catalog = glueCatalogLoader.loadCatalog();


        // Based on how many fields we want to partition, we create the Partition Spec
        PartitionSpec partitionSpec = getPartitionSpec(icebergSchema, partitionFieldList);
        // We create the Iceberg Table, using the Iceberg Catalog, Table Identifier, Schema parsed in Iceberg Schema Format and the partition spec
        createTable(catalog, outputTable, icebergSchema, partitionSpec);
        // Once the table has been created in the job or before, we load it
        TableLoader tableLoader = TableLoader.fromCatalog(glueCatalogLoader, outputTable);
        // Get RowType Schema from Iceberg Schema
        RowType rowType = FlinkSchemaUtil.convert(icebergSchema);

        // Iceberg DataStream sink builder
        FlinkSink.Builder flinkSinkBuilder = FlinkSink.<org.apache.avro.generic.GenericRecord>builderFor(
                        dataStream,
                        avroGenericRecordToRowDataMapper,
                        FlinkCompatibilityUtil.toTypeInfo(rowType))
                .tableLoader(tableLoader);

        // Returns the builder for the selected operation
        switch (icebergOperation) {
            case "upsert":
                // If operation is "upsert" we need to set up the equality fields
                return flinkSinkBuilder
                        .equalityFieldColumns(equalityFieldsList)
                        .upsert(true);
            case "overwrite":
                return flinkSinkBuilder
                        .overwrite(true);
            default:
                return flinkSinkBuilder;
        }
    }

}
