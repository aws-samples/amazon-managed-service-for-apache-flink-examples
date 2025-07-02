package com.amazonaws.services.msf;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.dropwizard.metrics.DropwizardMeterWrapper;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.Meter;

/**
 * No-op Map function exposing 3 custom metrics: generatedRecordCount, generatedRecordRatePerParallelism, and taskParallelism.
 * Note each subtask emits its own metrics.
 */
public class MetricEmitterNoOpMap<T> extends RichMapFunction<T, T> {
    private transient Counter recordCounter;
    private transient int taskParallelism = 0;
    private transient Meter recordMeter;

    @Override
    public void open(OpenContext openContext) throws Exception {
        this.recordCounter = getRuntimeContext()
                .getMetricGroup()
                .addGroup("kinesisAnalytics") // Automatically export metric to CloudWatch
                .counter("generatedRecordCount");

        this.recordMeter = getRuntimeContext()
                .getMetricGroup()
                .addGroup("kinesisAnalytics") // Automatically export metric to CloudWatch
                .meter("generatedRecordRatePerParallelism", new DropwizardMeterWrapper(new com.codahale.metrics.Meter()));

        getRuntimeContext()
                .getMetricGroup()
                .addGroup("kinesisAnalytics") // Automatically export metric to CloudWatch
                .gauge("taskParallelism", new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return taskParallelism;
                    }
                });

        // Capture the task parallelism
        taskParallelism = getRuntimeContext().getTaskInfo().getNumberOfParallelSubtasks();
    }

    @Override
    public T map(T record) throws Exception {
        recordCounter.inc();
        recordMeter.markEvent();

        return record;
    }
}
