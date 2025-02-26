package json.test;

import org.apache.flink.connector.testutils.formats.DummyInitializationContext;
import org.apache.flink.connector.testutils.source.deserialization.TestingDeserializationContext;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.util.function.SerializableSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;


public class SamplePojoSerializationTest {

    @Test
    public void testSerialization() throws IOException {
        // Create a MetricData POJO
        SamplePojo sample = new SamplePojo();
        sample.setMetricId("334220d6d0c0c7bd0e7868d985008e7ba5a4ec112e5a6b0ee3fab829d1777b5e");
        sample.setStationId("ABCdef0123456");
        sample.setTimestamp(java.time.Instant.now());
        sample.setComponent("Connector");
        sample.setEvseId(42);
        sample.setConnectorId(17);
        sample.setVariable("MyVariable");
        sample.setValueStr("MyValue");

        // Custom Jackson Object Mapper factory to configure serialization features
        SerializableSupplier<ObjectMapper> extendedObjectMapperFactory = () -> {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule()); // Add support for Java8 time types
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Force serializing timestamps as ISO8601
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // Enable JSON pretty-printer (not to be used in production)
            return objectMapper;
        };
        // Create and init the serialization schema
        JsonSerializationSchema<SamplePojo> serializationSchema = new JsonSerializationSchema<>(extendedObjectMapperFactory);
        serializationSchema.open(new DummyInitializationContext());

        // Serialize
        byte[] serializedBytes = serializationSchema.serialize(sample);

        // Print the JSON representation
        String jsonStr = new String(serializedBytes);
        System.out.println(jsonStr);

        // Create and init the deserialization schema
        JsonDeserializationSchema<SamplePojo> deserializationSchema = new JsonDeserializationSchema<>(SamplePojo.class);
        deserializationSchema.open(new TestingDeserializationContext());

        // Deserialize
        SamplePojo deserializedSample = deserializationSchema.deserialize(serializedBytes);


        // Assert that the deserialized object is equal to the original object
        assertEquals(sample, deserializedSample);
    }

}
