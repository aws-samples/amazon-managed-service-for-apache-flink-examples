package com.amazonaws.services.msf.kafka;

import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner;

import java.util.Arrays;

/**
 * Example of FlinkKafkaPartitioner which partitions by key.
 * <p>
 * The KafkaSink in DataStream API does not use the Kafka default partitioner by default.
 *
 * @param <T> record type
 */
public class KeyHashKafkaPartitioner<T> extends FlinkKafkaPartitioner<T> {

    @Override
    public int partition(T record, byte[] key, byte[] value, String targetTopic, int[] partitions) {
        if (key != null) {
            int numPartitions = partitions.length;
            int hash = Arrays.hashCode(key);
            return partitions[Math.abs(hash) % numPartitions];
        } else {
            return partitions[0];
        }
    }
}
