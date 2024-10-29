package com.amazonaws.services.msf.domain;

import org.apache.flink.types.PojoTestUtils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the serialization of {@link AggregateVehicleEvent}
 */
public class AggregateVehicleEventSerializationTest {

    @Test
    void serializationShouldNotFallbackToKryo() {
        // This Flink test-utils method verifies that serialization does not fall back to Kryo
        // It does not test whether the serialization/deserialization actually work
        // If you make a mistake defining the TypeInfo for the class, this test will succeed
        // but the serialization may still fail or give unexpected results.

        // This test is redundant if you implement the other test, below.

        PojoTestUtils.assertSerializedAsPojoWithoutKryo(AggregateVehicleEvent.class);
    }

    @Test
    void shouldSerializeAndDeserializeWithoutKryo() throws IOException {
        // This test is an extension of the previous test. It actually serializes and deserializes the record
        // and verifies that the content was correctly serialized/deserialized
        // If you make a mistake defining the TypeInfo for the class, this test will fail
        AggregateVehicleEvent record = new AggregateVehicleEvent(
                "V123456X"
                , 42L,
                Map.ofEntries(
                        Map.entry("speed", 100L),
                        Map.entry("rpm", 2000L),
                        Map.entry("fuelLevel", 12345L)
                ),
                42);


        // Serialize and deserialize the record forcing Kryo
        // This will fail if Flink has to fallback to Kryo
        AggregateVehicleEvent deserialized = FlinkSerializationTestUtils.serializeDeserializeNoKryo(record, AggregateVehicleEvent.class);

        // Compare the original record with the deserialized record
        // If you made a mistake in defining the TypeInfo, this comparison will fail
        assertEquals(record.getVin(), deserialized.getVin());
        assertEquals(record.getTimestamp(), deserialized.getTimestamp());
        assertEquals(record.getAverageSensorData(), deserialized.getAverageSensorData());
        assertEquals(record.getCountDistinctWarnings(), deserialized.getCountDistinctWarnings());
    }
}
