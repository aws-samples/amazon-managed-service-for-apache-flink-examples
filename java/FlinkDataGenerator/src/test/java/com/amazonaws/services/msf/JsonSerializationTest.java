package com.amazonaws.services.msf;

import com.amazonaws.services.msf.domain.StockPrice;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JsonSerializationTest {

    @Test
    public void testStockPriceJsonSerialization() throws Exception {
        StockPrice stock = new StockPrice("2024-05-28T19:53:17.497201", "AMZN", 42.88f);
        
        JsonSerializationSchema<StockPrice> serializer = new JsonSerializationSchema<StockPrice>();
        serializer.open(null);
        
        byte[] jsonBytes = serializer.serialize(stock);
        String json = new String(jsonBytes);
        
        System.out.println("Actual JSON: " + json);
        
        // Verify the JSON contains event_time (not eventTime)
        assertTrue(json.contains("\"event_time\""), "JSON should contain 'event_time' field");
        assertFalse(json.contains("\"eventTime\""), "JSON should not contain 'eventTime' field");
        
        // Verify other fields
        assertTrue(json.contains("\"ticker\":\"AMZN\""), "JSON should contain ticker");
        assertTrue(json.contains("\"price\":42.88"), "JSON should contain price");
    }
}
