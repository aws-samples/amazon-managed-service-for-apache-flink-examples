package com.amazonaws.services.msf.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class StockPriceTest {

    @Test
    public void testStockCreation() {
        StockPrice stock = new StockPrice("2024-01-15T10:30:45", "AAPL", 150.25f);
        
        assertEquals("2024-01-15T10:30:45", stock.getEventTime());
        assertEquals("AAPL", stock.getTicker());
        assertEquals(150.25f, stock.getPrice(), 0.001);
    }

    @Test
    public void testStockToString() {
        StockPrice stock = new StockPrice("2024-01-15T10:30:45", "AAPL", 150.25f);
        String expected = "Stock{event_time='2024-01-15T10:30:45', ticker='AAPL', price=150.25}";
        assertEquals(expected, stock.toString());
    }

    @Test
    public void testStockSetters() {
        StockPrice stock = new StockPrice();
        stock.setEventTime("2024-01-15T10:30:45");
        stock.setTicker("MSFT");
        stock.setPrice(200.50f);
        
        assertEquals("2024-01-15T10:30:45", stock.getEventTime());
        assertEquals("MSFT", stock.getTicker());
        assertEquals(200.50f, stock.getPrice(), 0.001);
    }

    @Test
    public void testStockHashCodeForPartitioning() {
        // Create test stock objects
        StockPrice stock1 = new StockPrice("2024-01-15T10:30:45", "AAPL", 150.25f);
        StockPrice stock2 = new StockPrice("2024-01-15T10:30:46", "MSFT", 200.50f);
        StockPrice stock3 = new StockPrice("2024-01-15T10:30:45", "AAPL", 150.25f); // Same as stock1

        // Test that hashCode is consistent for equal objects
        assertEquals("Equal stock objects should have same hashCode",
                stock1.hashCode(), stock3.hashCode());

        // Test that equals works correctly
        assertEquals("Same stock objects should be equal", stock1, stock3);
        assertNotEquals("Different stock objects should not be equal", stock1, stock2);

        // Test that different stocks likely have different hashCodes
        assertNotEquals("Different stock objects should likely have different hashCodes",
                stock1.hashCode(), stock2.hashCode());

        // Test that hashCode can be used as partition key (should not throw exception)
        String partitionKey1 = String.valueOf(stock1.hashCode());
        String partitionKey2 = String.valueOf(stock2.hashCode());

        assertNotNull("Partition key should not be null", partitionKey1);
        assertNotNull("Partition key should not be null", partitionKey2);
        assertFalse("Partition key should not be empty", partitionKey1.isEmpty());
        assertFalse("Partition key should not be empty", partitionKey2.isEmpty());
    }
}
