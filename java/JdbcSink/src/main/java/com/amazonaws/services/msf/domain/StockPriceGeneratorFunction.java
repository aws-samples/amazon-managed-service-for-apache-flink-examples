package com.amazonaws.services.msf.domain;

import com.github.javafaker.Faker;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generator function that creates realistic fake StockPrice objects using JavaFaker.
 * Implements GeneratorFunction to work with DataGeneratorSource.
 */
public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {
    
    // JavaFaker instance for generating fake data
    private static final Faker faker = new Faker(Locale.ENGLISH);
    
    // Date formatter for ISO format timestamps
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    @Override
    public StockPrice map(Long value) throws Exception {
        // Generate current timestamp in ISO format
        String timestamp = LocalDateTime.now().format(ISO_FORMATTER);
        
        // Use JavaFaker's Stock class to generate realistic NASDAQ ticker symbolsy
        String symbol = faker.stock().nsdqSymbol();
        
        // Generate realistic stock price between $1.00 and $500.00
        // Using faker to generate a base price and then applying some randomness
        double basePrice = faker.number().randomDouble(2, 1, 500);
        
        // Add some volatility to make prices more realistic
        // Apply a small random change (-5% to +5%)
        double volatilityPercent = faker.number().randomDouble(4, -5, 5);
        double finalPrice = basePrice * (1 + volatilityPercent / 100.0);
        
        // Ensure price is positive and round to 2 decimal places
        finalPrice = Math.max(0.01, finalPrice);
        BigDecimal price = BigDecimal.valueOf(finalPrice).setScale(2, RoundingMode.HALF_UP);
        
        return new StockPrice(symbol, timestamp, price);
    }
}
