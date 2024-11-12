package com.amazonaws.services.msf.datagen;

import com.amazonaws.services.msf.avro.AvroSchemaUtils;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AvroGenericStockTradeGeneratorFunctionTest {

    @Test
    void generateRecord() throws Exception {
        Schema avroSchema = AvroSchemaUtils.loadSchema();
        AvroGenericStockTradeGeneratorFunction generatorFunction = new AvroGenericStockTradeGeneratorFunction(avroSchema);

        GenericRecord record = generatorFunction.map(42L);


        assertInstanceOf(String.class, record.get("timestamp"));
        assertInstanceOf(String.class, record.get("symbol"));
        assertInstanceOf(Float.class, record.get("price"));
        assertInstanceOf(Integer.class, record.get("volumes"));
    }

}