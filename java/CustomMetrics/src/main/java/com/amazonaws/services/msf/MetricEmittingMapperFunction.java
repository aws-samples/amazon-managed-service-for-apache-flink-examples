package com.amazonaws.services.msf;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;

/**
 * NoOp mapper function which acts as a pass through for the records. It would also publish the custom metric for
 * the number of received records.
 */
class MetricEmittingMapperFunction extends RichMapFunction<SpeedRecord, SpeedRecord> {
    private static final double averageDecay = 0.1;

    private transient Counter counter;
    private transient double runningAverage;
    private final String customMetricName;

    public MetricEmittingMapperFunction(final String customMetricName) {
        this.customMetricName = customMetricName;
    }

    @Override
    public void open(Configuration config) {
        this.counter = getRuntimeContext().getMetricGroup()
                .addGroup("kinesisanalytics")
                .counter(customMetricName + "Total");

        getRuntimeContext().getMetricGroup()
                .addGroup("kinesisanalytics")
                .gauge(customMetricName + "Average", () -> runningAverage);

    }

    @Override
    public SpeedRecord map(SpeedRecord value) {
        counter.inc();
        runningAverage = runningAverage * (1 - averageDecay) +
                value.speed * averageDecay;
        return value;
    }
}