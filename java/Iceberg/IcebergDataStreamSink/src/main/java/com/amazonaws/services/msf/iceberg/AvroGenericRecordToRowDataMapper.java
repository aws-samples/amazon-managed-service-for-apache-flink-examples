package com.amazonaws.services.msf.iceberg;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.formats.avro.AvroToRowDataConverters;
import org.apache.flink.formats.avro.typeutils.AvroSchemaConverter;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

public class AvroGenericRecordToRowDataMapper implements MapFunction<GenericRecord, RowData> {
    private final AvroToRowDataConverters.AvroToRowDataConverter converter;

    AvroGenericRecordToRowDataMapper(RowType rowType) {
        this.converter = AvroToRowDataConverters.createRowConverter(rowType);
    }

    public RowData map(GenericRecord genericRecord) throws Exception {
        return (RowData)this.converter.convert(genericRecord);
    }

    public static AvroGenericRecordToRowDataMapper forAvroSchema(Schema avroSchema) {
        DataType dataType = AvroSchemaConverter.convertToDataType(avroSchema.toString());
        LogicalType logicalType = TypeConversions.fromDataToLogicalType(dataType);
        RowType rowType = (RowType) logicalType;
        return new AvroGenericRecordToRowDataMapper(rowType);
    }
}