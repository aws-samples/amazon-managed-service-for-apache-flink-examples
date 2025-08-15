package com.amazonaws.services.msf.domain;

import com.amazonaws.services.msf.avro.TemperatureSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureSampleGeneratorTest {

    @Test
    void testGenerateTemperatureSample() throws Exception {
        TemperatureSampleGenerator generator = new TemperatureSampleGenerator();
        
        TemperatureSample sample = generator.map(1L);
        
        assertNotNull(sample);
        assertTrue(sample.getSensorId() >= 0);
        assertTrue(sample.getSensorId() < 1000);
        assertNotNull(sample.getRoom());
        assertTrue(sample.getRoom().startsWith("room"));
        assertTrue(sample.getTemperature() >= 18.0f);
        assertTrue(sample.getTemperature() <= 30.0f);
        assertNotNull(sample.getSampleTime());
    }

    @Test
    void testMultipleSamplesHaveDifferentValues() throws Exception {
        TemperatureSampleGenerator generator = new TemperatureSampleGenerator();
        
        TemperatureSample sample1 = generator.map(1L);
        TemperatureSample sample2 = generator.map(2L);
        
        // At least one property should be different (sensorId, room, or temperature)
        boolean isDifferent = sample1.getSensorId() != sample2.getSensorId() ||
                             !sample1.getRoom().equals(sample2.getRoom()) || 
                             sample1.getTemperature() != sample2.getTemperature();
        assertTrue(isDifferent);
    }
}
