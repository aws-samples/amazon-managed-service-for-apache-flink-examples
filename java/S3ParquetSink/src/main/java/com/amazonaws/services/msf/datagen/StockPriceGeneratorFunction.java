package com.amazonaws.services.msf.datagen;

import com.amazonaws.services.msf.avro.StockPrice;
import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.time.Instant;

public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {
    private static final String[] TICKERS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};

    @Override
    public StockPrice map(Long aLong) {
        return new StockPrice(
                TICKERS[RandomUtils.nextInt(0, TICKERS.length)],
                RandomUtils.nextFloat(10, 100),
                RandomUtils.nextInt(1, 10000),
                Instant.now().toEpochMilli());
    }
}