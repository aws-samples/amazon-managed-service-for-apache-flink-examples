package com.amazonaws.services.msf.datagen;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.lang3.RandomUtils;

import java.time.Instant;

/**
 * Function used by DataGen source to generate random records as AVRO GenericRecord.
 * <p>
 * The generator assumes that the AVRO schema provided contains the generated fields
 */
public class AvroGenericStockTradeGeneratorFunction implements GeneratorFunction<Long, GenericRecord> {

    private static final String[] SYMBOLS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};

    private Schema avroSchema;

    public AvroGenericStockTradeGeneratorFunction(Schema avroSchema) {
        this.avroSchema = avroSchema;
    }

    /**
     * Generates a random trade
     */
    @Override
    public GenericRecord map(Long value) {
        GenericData.Record record = new GenericData.Record(avroSchema);

        String symbol = SYMBOLS[RandomUtils.nextInt(0, SYMBOLS.length)];
        float price = RandomUtils.nextFloat(0, 10);
        int volumes = RandomUtils.nextInt(0, 1000000);
        String timestamp = Instant.now().toString();

        record.put("symbol", symbol);
        record.put("price", price);
        record.put("volumes", volumes);
        record.put("timestamp", timestamp);

        return record;
    }
}
