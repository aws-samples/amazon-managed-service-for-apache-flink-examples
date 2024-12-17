package com.amazonaws.services.msf.aggregation;

import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.amazonaws.services.msf.domain.AggregateVehicleEvent;
import com.amazonaws.services.msf.domain.VehicleEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ProcessWindowFunction that aggregates the sensor data and warning lights for a vehicle.
 * <p>
 * The actual implementation is not relevant for this example.
 */
public class AggregateVehicleEventsWindowFunction extends ProcessWindowFunction<VehicleEvent, AggregateVehicleEvent, String, TimeWindow> {
    @Override
    public void process(String vin, ProcessWindowFunction<VehicleEvent, AggregateVehicleEvent, String, TimeWindow>.Context context, Iterable<VehicleEvent> events, Collector<AggregateVehicleEvent> out) throws Exception {
        Map<String, Long> sensorTotal = new HashMap<>();
        Set<String> warnings = new HashSet<>();
        long count = 0;
        for (VehicleEvent event : events) {
            // Average of sensor data
            for (Map.Entry<String, Long> sensorMeasurement : event.getSensorData().entrySet()) {
                String sensorId = sensorMeasurement.getKey();
                long newSensorData = sensorMeasurement.getValue();
                sensorTotal.merge(sensorId, newSensorData, Long::sum);
            }
            // Unique warnings
            warnings.addAll(event.getWarningLights());

            count++;
        }

        final long sensorDataCount = count;
        Map<String, Long> sensorAverage = sensorTotal.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / sensorDataCount));

        out.collect(new AggregateVehicleEvent(
                vin,
                context.window().maxTimestamp(),
                sensorAverage,
                warnings.size()
        ));
    }
}
