package com.amazonaws.services.msf.map;

import com.amazonaws.services.msf.domain.TemperatureSample;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.connector.prometheus.sink.PrometheusTimeSeries;

/**
 * Maps a TemperatureSample to a PrometheusTimeSeries, which is the input object for the PrometheusSink.
 */
public class TemperatureSampleToPrometheusTimeSeriesMapper implements MapFunction<TemperatureSample, PrometheusTimeSeries> {
    @Override
    public PrometheusTimeSeries map(TemperatureSample value) throws Exception {
        return PrometheusTimeSeries.builder()
                .withMetricName("temperature")
                .addLabel("roomId", value.getRoomId())
                .addLabel("sensorId", value.getSensorId())
                .addSample(value.getTemperature(), value.getTimestamp())
                .build();
    }
}
