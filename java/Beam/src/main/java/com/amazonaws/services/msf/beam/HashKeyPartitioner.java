package com.amazonaws.services.msf.beam;

import org.apache.beam.sdk.io.aws2.kinesis.KinesisPartitioner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class HashKeyPartitioner implements KinesisPartitioner<Document> {
    private final Logger LOGGER = LogManager.getLogger(HashKeyPartitioner.class);

    /**
     * Creates a partition key based on text of Document object
     *
     * @param doc - a Document object
     * @return partition key as hash code
     */
    @Override
    public @NotNull @UnknownKeyFor @NonNull @Initialized
        String getPartitionKey(Document doc) {
        String partitionKey = String.valueOf(
                Arrays.hashCode(doc.getText().getBytes())
        );

        LOGGER.info("Partition key is {}", partitionKey);
        return partitionKey;
    }
}
