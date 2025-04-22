package com.amazonaws.services.msf;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.file.src.FileSourceSplit;
import org.apache.flink.formats.avro.AbstractAvroBulkFormat;

import java.util.function.Function;

/**
 * Simple BulkFormat to read AVRO files into SpecificRecord
 * @param <O>
 */
public class AvroSpecificRecordBulkFormat<O extends org.apache.avro.specific.SpecificRecord>
        extends AbstractAvroBulkFormat<GenericRecord, O, FileSourceSplit> {

    private final TypeInformation<O> producedTypeInfo;
    private final org.apache.avro.Schema avroReaderSchema;


    public AvroSpecificRecordBulkFormat(Class<O> recordClass, org.apache.avro.Schema avroSchema) {
        super(avroSchema);
        avroReaderSchema = avroSchema;
        producedTypeInfo = TypeInformation.of(recordClass);
    }

    @Override
    protected GenericRecord createReusedAvroRecord() {
        return new GenericData.Record(avroReaderSchema);
    }

    @Override
    protected Function<GenericRecord, O> createConverter() {
        return genericRecord -> (O) SpecificData.get().deepCopy(avroReaderSchema, genericRecord);
    }

    @Override
    public TypeInformation<O> getProducedType() {
        return producedTypeInfo;
    }

}
