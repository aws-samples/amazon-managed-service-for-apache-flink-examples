package com.amazonaws.services.msf.source;

import com.amazonaws.services.msf.domain.StockPrice;
import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import java.time.Instant;

/**
 * Function used by DataGen source to generate random records as com.amazonaws.services.msf.pojo.StockPrice POJOs.
 *
 * The generator mimics the behavior of AvroGenericStockTradeGeneratorFunction
 * from the IcebergDataStreamSink example.
 */
public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {

    private static final String[] SYMBOLS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};

    @Override
    public StockPrice map(Long sequence) throws Exception {
        String symbol = SYMBOLS[RandomUtils.nextInt(0, SYMBOLS.length)];
        float price = RandomUtils.nextFloat(0, 10);
        int volumes = RandomUtils.nextInt(0, 1000000);
        String timestamp = Instant.now().toString();

        return new StockPrice(timestamp, symbol, price, volumes);
    }
}