package com.amazonaws.services.msf.windowing;

import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.sql.Timestamp;
import java.time.Instant;

public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {
    private static final String[] TICKERS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};

    @Override
    public StockPrice map(Long aLong) {
       return new StockPrice(
               new Timestamp(Instant.now().toEpochMilli()),
               TICKERS[RandomUtils.nextInt(0, TICKERS.length)],
               RandomUtils.nextDouble(10,100)
       );
    }
}
