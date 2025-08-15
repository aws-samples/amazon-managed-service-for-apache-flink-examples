package com.amazonaws.services.msf.domain;

import com.amazonaws.services.msf.avro.TemperatureSample;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.time.Instant;
import java.util.Random;

public class TemperatureSampleGenerator implements GeneratorFunction<Long, TemperatureSample> {
    
    private static final String[] ROOMS = {"room1", "room2", "room3", "room4", "room5"};
    private final Random random = new Random();

    @Override
    public TemperatureSample map(Long index) throws Exception {
        String room = ROOMS[random.nextInt(ROOMS.length)];
        double temperature = 18.0 + random.nextDouble() * 12.0; // 18-30°C
        int sensorId = random.nextInt(100); // Random sensor ID
        return TemperatureSample.newBuilder()
                .setSensorId(sensorId)
                .setRoom(room)
                .setTemperature((float) temperature)
                .setSampleTime(Instant.now())
                .build();
    }
}
