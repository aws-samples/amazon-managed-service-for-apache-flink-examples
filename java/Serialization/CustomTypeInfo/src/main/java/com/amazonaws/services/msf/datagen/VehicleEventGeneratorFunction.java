package com.amazonaws.services.msf.datagen;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

import com.amazonaws.services.msf.domain.VehicleEvent;
import org.apache.commons.lang3.RandomUtils;

import java.util.List;
import java.util.Map;

/**
 * Implementation of GeneratorFunction that generates random {@link VehicleEvent}
 */
public class VehicleEventGeneratorFunction implements GeneratorFunction<Long, VehicleEvent> {

    private static final String[] WARNING_LIGHTS = {"fuelPressure", "oilPressure", "tirePressure"};

    @Override
    public VehicleEvent map(Long value) throws Exception {
        return new VehicleEvent(
                String.format("V%08d", RandomUtils.nextInt(0, 100)),
                System.currentTimeMillis(),
                Map.of(
                        "speed", RandomUtils.nextLong(0, 120),
                        "rpm", RandomUtils.nextLong(1000, 10000),
                        "fuelLevel", RandomUtils.nextLong(0, 100)
                ),
                List.of(WARNING_LIGHTS[RandomUtils.nextInt(0, WARNING_LIGHTS.length)])
        );
    }
}
