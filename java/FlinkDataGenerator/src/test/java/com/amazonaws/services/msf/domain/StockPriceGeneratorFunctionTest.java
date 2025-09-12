package com.amazonaws.services.msf.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

public class StockPriceGeneratorFunctionTest {
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

    @Test
    public void testStockGeneratorFunction() throws Exception {
        StockPriceGeneratorFunction generator = new StockPriceGeneratorFunction();
        
        // Generate a stock record
        StockPrice stock = generator.map(1L);
        
        // Verify the stock is not null
        assertNotNull(stock);
        
        // Verify event_time is not null and not empty
        assertNotNull(stock.getEventTime());
        assertFalse(stock.getEventTime().isEmpty());
        
        // Verify ticker is one of the expected values
        List<String> expectedTickers = Arrays.asList(TICKERS);
        assertTrue(expectedTickers.contains(stock.getTicker()), "Ticker should be one of the expected values");
        
        // Verify price is within expected range (0 to 100)
        assertTrue(stock.getPrice() >= 0, "Price should be >= 0");
        assertTrue(stock.getPrice() <= 100, "Price should be <= 100");
        
        // Verify price has at most 2 decimal places
        String priceStr = String.valueOf(stock.getPrice());
        int decimalIndex = priceStr.indexOf('.');
        if (decimalIndex != -1) {
            int decimalPlaces = priceStr.length() - decimalIndex - 1;
            assertTrue(decimalPlaces <= 2, "Price should have at most 2 decimal places");
        }
    }

    @Test
    public void testMultipleGenerations() throws Exception {
        StockPriceGeneratorFunction generator = new StockPriceGeneratorFunction();
        
        // Generate multiple records to ensure randomness
        for (int i = 0; i < 10; i++) {
            StockPrice stock = generator.map((long) i);
            assertNotNull(stock);
            assertNotNull(stock.getEventTime());
            assertNotNull(stock.getTicker());
            assertTrue(stock.getPrice() >= 0 && stock.getPrice() <= 100);
        }
    }
}
