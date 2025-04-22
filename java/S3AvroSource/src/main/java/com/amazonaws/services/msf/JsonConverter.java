package com.amazonaws.services.msf;

import org.apache.avro.Schema;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;

import java.io.ByteArrayOutputStream;

/**
 * Simple converter for Avro records to JSON. Not designed for production.
 */
public class JsonConverter<T extends org.apache.avro.specific.SpecificRecord> extends RichMapFunction<T, String> {

    private final Schema avroSchema;
    private transient SpecificDatumWriter<T> writer;

    public JsonConverter(Schema avroSchema) {
        this.avroSchema = avroSchema;
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        this.writer = new SpecificDatumWriter<>(avroSchema);
    }

    @Override
    public String map(T record) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonEncoder encoder = EncoderFactory.get().jsonEncoder(record.getSchema(), outputStream);
        writer.write(record, encoder);
        encoder.flush();
        return outputStream.toString();
    }
}
