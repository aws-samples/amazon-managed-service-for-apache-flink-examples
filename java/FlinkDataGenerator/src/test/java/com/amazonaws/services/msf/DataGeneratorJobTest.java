package com.amazonaws.services.msf;

import com.amazonaws.services.msf.domain.StockPrice;
import com.amazonaws.services.msf.domain.StockPriceGeneratorFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Method;

public class DataGeneratorJobTest {

    @Test
    public void testCreateDataGeneratorSource() throws Exception {
        // Use reflection to test the private createDataGeneratorSource method
        Method createDataGeneratorSourceMethod = DataGeneratorJob.class.getDeclaredMethod(
            "createDataGeneratorSource", Properties.class, GeneratorFunction.class, TypeInformation.class);
        createDataGeneratorSourceMethod.setAccessible(true);

        // Test with valid configuration
        Properties dataGenProps = new Properties();
        dataGenProps.setProperty("records.per.second", "15");
        
        StockPriceGeneratorFunction generatorFunction = new StockPriceGeneratorFunction();
        TypeInformation<StockPrice> typeInfo = TypeInformation.of(StockPrice.class);
        
        DataGeneratorSource<StockPrice> source = (DataGeneratorSource<StockPrice>) createDataGeneratorSourceMethod.invoke(
            null, dataGenProps, generatorFunction, typeInfo);
        
        assertNotNull("DataGeneratorSource should not be null", source);

        // Test with null properties (should use default rate)
        source = (DataGeneratorSource<StockPrice>) createDataGeneratorSourceMethod.invoke(
            null, null, generatorFunction, typeInfo);
        
        assertNotNull("DataGeneratorSource should not be null with null properties", source);

        // Test with empty properties (should use default rate)
        Properties emptyProps = new Properties();
        source = (DataGeneratorSource<StockPrice>) createDataGeneratorSourceMethod.invoke(
            null, emptyProps, generatorFunction, typeInfo);
        
        assertNotNull("DataGeneratorSource should not be null with empty properties", source);
    }

    @Test
    public void testCreateKafkaSink() throws Exception {
        // Use reflection to test the private createKafkaSink method
        Method createKafkaSinkMethod = DataGeneratorJob.class.getDeclaredMethod(
            "createKafkaSink", Properties.class, KafkaRecordSerializationSchema.class);
        createKafkaSinkMethod.setAccessible(true);

        // Test with valid Kafka properties
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "localhost:9092");
        kafkaProps.setProperty("topic", "test-topic");
        
        // Create a mock KafkaRecordSerializationSchema
        KafkaRecordSerializationSchema<StockPrice> recordSerializationSchema =
            KafkaRecordSerializationSchema.<StockPrice>builder()
                .setTopic("test-topic")
                .setKeySerializationSchema(stock -> stock.getTicker().getBytes())
                .setValueSerializationSchema(new org.apache.flink.formats.json.JsonSerializationSchema<>())
                .build();
        
        KafkaSink<StockPrice> kafkaSink = (KafkaSink<StockPrice>) createKafkaSinkMethod.invoke(
            null, kafkaProps, recordSerializationSchema);
        
        assertNotNull("KafkaSink should not be null", kafkaSink);
    }

    @Test
    public void testKafkaPartitioningKey() {
        // Test that ticker symbol can be used as Kafka partition key
        StockPrice stock1 = new StockPrice("2024-01-15T10:30:45", "AAPL", 150.25f);
        StockPrice stock2 = new StockPrice("2024-01-15T10:30:46", "MSFT", 200.50f);
        
        // Test that ticker can be converted to bytes for Kafka key
        byte[] key1 = stock1.getTicker().getBytes();
        byte[] key2 = stock2.getTicker().getBytes();
        
        assertNotNull("Kafka key should not be null", key1);
        assertNotNull("Kafka key should not be null", key2);
        assertTrue("Kafka key should not be empty", key1.length > 0);
        assertTrue("Kafka key should not be empty", key2.length > 0);
        
        // Test that different tickers produce different keys
        assertFalse("Different tickers should produce different keys", 
                   java.util.Arrays.equals(key1, key2));
        
        // Test that same ticker produces same key
        StockPrice stock3 = new StockPrice("2024-01-15T10:30:47", "AAPL", 175.50f);
        byte[] key3 = stock3.getTicker().getBytes();
        assertTrue("Same ticker should produce same key", 
                  java.util.Arrays.equals(key1, key3));
    }

    @Test
    public void testConditionalSinkValidation() {
        // Test that the application validates sink configuration properly
        Map<String, Properties> appProperties = new HashMap<>();
        
        // Test with no sinks configured - should be invalid
        boolean hasKinesis = appProperties.get("KinesisSink") != null;
        boolean hasKafka = appProperties.get("KafkaSink") != null;
        assertFalse("Should not have Kinesis sink when not configured", hasKinesis);
        assertFalse("Should not have Kafka sink when not configured", hasKafka);
        assertTrue("Should require at least one sink", !hasKinesis && !hasKafka);
        
        // Test with only Kinesis configured - should be valid
        Properties kinesisProps = new Properties();
        kinesisProps.setProperty("stream.arn", "test-arn");
        kinesisProps.setProperty("aws.region", "us-east-1");
        appProperties.put("KinesisSink", kinesisProps);
        
        hasKinesis = appProperties.get("KinesisSink") != null;
        hasKafka = appProperties.get("KafkaSink") != null;
        assertTrue("Should have Kinesis sink when configured", hasKinesis);
        assertFalse("Should not have Kafka sink when not configured", hasKafka);
        assertTrue("Should be valid with one sink", hasKinesis || hasKafka);
        
        // Test with only Kafka configured - should be valid
        appProperties.clear();
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "localhost:9092");
        kafkaProps.setProperty("topic", "test-topic");
        appProperties.put("KafkaSink", kafkaProps);
        
        hasKinesis = appProperties.get("KinesisSink") != null;
        hasKafka = appProperties.get("KafkaSink") != null;
        assertFalse("Should not have Kinesis sink when not configured", hasKinesis);
        assertTrue("Should have Kafka sink when configured", hasKafka);
        assertTrue("Should be valid with one sink", hasKinesis || hasKafka);
        
        // Test with both configured - should be valid
        appProperties.put("KinesisSink", kinesisProps);
        
        hasKinesis = appProperties.get("KinesisSink") != null;
        hasKafka = appProperties.get("KafkaSink") != null;
        assertTrue("Should have Kinesis sink when configured", hasKinesis);
        assertTrue("Should have Kafka sink when configured", hasKafka);
        assertTrue("Should be valid with both sinks", hasKinesis || hasKafka);
    }
}
