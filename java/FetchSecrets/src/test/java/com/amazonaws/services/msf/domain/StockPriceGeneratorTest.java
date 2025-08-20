package com.amazonaws.services.msf.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockPriceGeneratorTest {

    private static final List<String> EXPECTED_SYMBOLS = Arrays.asList(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA", "NFLX"
    );

    @Test
    void testGenerateStockPrice() throws Exception {
        StockPriceGenerator generator = new StockPriceGenerator();
        
        StockPrice stockPrice = generator.map(1L);
        
        assertNotNull(stockPrice);
        assertNotNull(stockPrice.getSymbol());
        assertNotNull(stockPrice.getTimestamp());
        assertTrue(stockPrice.getPrice() > 0);
        
        // Verify symbol is one of the expected values
        assertTrue(EXPECTED_SYMBOLS.contains(stockPrice.getSymbol()));
        
        // Verify price is in expected range (50-500)
        assertTrue(stockPrice.getPrice() >= 50.0);
        assertTrue(stockPrice.getPrice() <= 500.0);
        
        // Verify timestamp is valid ISO format
        assertDoesNotThrow(() -> Instant.parse(stockPrice.getTimestamp()));
    }
}
