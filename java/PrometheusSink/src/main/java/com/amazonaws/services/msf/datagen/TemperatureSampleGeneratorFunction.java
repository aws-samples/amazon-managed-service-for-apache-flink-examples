package com.amazonaws.services.msf.datagen;

import com.amazonaws.services.msf.domain.TemperatureSample;
import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

public class TemperatureSampleGeneratorFunction implements GeneratorFunction<Long, TemperatureSample> {

    private static final long NR_OF_ROOMS = 100;
    private static final int NR_OF_SENSORS_PER_ROOM = 10;

    // Generate a semi-random TemperatureSample
    @Override
    public TemperatureSample map(Long value) throws Exception {
        long roomId = value % NR_OF_ROOMS;
        long sensorId = RandomUtils.nextInt(0, NR_OF_SENSORS_PER_ROOM) + roomId * NR_OF_ROOMS;

        return new TemperatureSample(
                String.format("R%05d", roomId),
                String.format("S%09d", sensorId),
                System.currentTimeMillis(),
                RandomUtils.nextDouble(20.0, 30.0)
        );
    }
}
