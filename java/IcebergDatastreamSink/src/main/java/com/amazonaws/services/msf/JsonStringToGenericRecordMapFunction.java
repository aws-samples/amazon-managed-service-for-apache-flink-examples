package com.amazonaws.services.msf;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.flink.api.common.functions.MapFunction;
import java.io.File;
import java.io.InputStream;

public class JsonStringToGenericRecordMapFunction implements MapFunction<String, GenericRecord> {

    @Override
    public GenericRecord map(String message) throws Exception {
        InputStream inputStream = StreamingJob.class.getClassLoader().getResourceAsStream("trade.avsc");

        Schema schema = new Schema.Parser().parse(inputStream);
        DatumReader<GenericRecord> reader = new GenericDatumReader<GenericRecord>(schema);
        return reader.read(null, DecoderFactory.get().jsonDecoder(schema,message));
    }
}


