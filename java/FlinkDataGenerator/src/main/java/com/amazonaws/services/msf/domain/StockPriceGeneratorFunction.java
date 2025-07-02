package com.amazonaws.services.msf.domain;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Generator function that creates random Stock objects.
 * Implements GeneratorFunction to work with DataGeneratorSource.
 *
 * Modify this class to generate a different record type
 */
public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {
    // Stock tickers to randomly choose from (same as Python data generator)
    private static final String[] TICKERS = {
            "AAPL",
            "MSFT",
            "AMZN",
            "GOOGL",
            "META",
            "NVDA",
            "TSLA",
            "INTC",
            "ADBE",
            "NFLX",
            "PYPL",
            "CSCO",
            "PEP",
            "AVGO",
            "AMD",
            "COST",
            "QCOM",
            "AMGN",
            "SBUX",
            "BKNG"
    };
    
    // Random number generator
    private static final Random RANDOM = new Random();
    
    // Date formatter for ISO format timestamps
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    @Override
    public StockPrice map(Long value) throws Exception {
        // Generate current timestamp in ISO format
        String eventTime = LocalDateTime.now().format(ISO_FORMATTER);
        
        // Randomly select a ticker
        String ticker = TICKERS[RANDOM.nextInt(TICKERS.length)];
        
        // Generate random price between 0 and 100, rounded to 2 decimal places
        float price = Math.round(RANDOM.nextFloat() * 100 * 100.0f) / 100.0f;
        
        return new StockPrice(eventTime, ticker, price);
    }
}
