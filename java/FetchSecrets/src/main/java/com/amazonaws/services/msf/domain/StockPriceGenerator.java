package com.amazonaws.services.msf.domain;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.time.Instant;
import java.util.Random;

public class StockPriceGenerator implements GeneratorFunction<Long, StockPrice> {
    
    private static final String[] SYMBOLS = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA", "NFLX"};
    private final Random random = new Random();

    @Override
    public StockPrice map(Long value) throws Exception {
        String symbol = SYMBOLS[random.nextInt(SYMBOLS.length)];
        double price = 50 + random.nextDouble() * 450; // Price between $50-$500
        String timestamp = Instant.now().toString();
        
        return new StockPrice(symbol, timestamp, price);
    }
}
