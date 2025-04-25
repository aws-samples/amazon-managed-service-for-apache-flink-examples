package com.amazonaws.services.msf.deaggregation;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kinesis.source.serialization.KinesisDeserializationSchema;
import org.apache.flink.util.Collector;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.kinesis.retrieval.AggregatorUtil;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper adding de-aggregation to any DeserializationSchema
 *
 * @param <T> type returned by the DeserializationSchema
 */
public class KinesisDeaggregatingDeserializationSchemaWrapper<T> implements KinesisDeserializationSchema<T> {
    private static final long serialVersionUID = 1L;

    private final DeserializationSchema<T> deserializationSchema;
    private final RecordDeaggregator<Record> recordDeaggregator = new RecordDeaggregator();

    public KinesisDeaggregatingDeserializationSchemaWrapper(DeserializationSchema<T> deserializationSchema) {
        this.deserializationSchema = deserializationSchema;
    }

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
        this.deserializationSchema.open(context);
    }

    @Override
    public void deserialize(Record record, String stream, String shardId, Collector<T> output) {
        try {
            // Deaggregate the record read from the stream, if required
            List<KinesisClientRecord> deaggregatedRecords = recordDeaggregator.deaggregate(record);
            // Deserialize each deaggregated record, independently
            for (KinesisClientRecord deaggregatedRecord : deaggregatedRecords) {
                T deserializedRecord = deserializationSchema.deserialize(toByteArray(deaggregatedRecord.data()));
                output.collect(deserializedRecord);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while deaggregating Kinesis record from stream " + stream + ", shardId " + shardId, e);
        }
    }

    @Override
    public TypeInformation<T> getProducedType() {
        return deserializationSchema.getProducedType();
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    /**
     * De-aggregate software.amazon.awssdk.services.kinesis.model.Record into a collection
     * of software.amazon.kinesis.retrieval.KinesisClientRecord.
     * <p>
     * The code of this class is copied from
     * https://github.com/awslabs/kinesis-aggregation/blob/master/java/KinesisDeaggregatorV2/src/main/java/com/amazonaws/kinesis/deagg/RecordDeaggregator.java
     * which is not available in Maven Central.
     * See https://github.com/awslabs/kinesis-aggregation/issues/120
     */
    private static class RecordDeaggregator<T> implements Serializable {

        /**
         * Method to deaggregate a single Kinesis Record into a List of UserRecords
         *
         * @param inputRecord The Kinesis Record provided by AWS Lambda or Kinesis SDK
         * @return A list of Kinesis UserRecord objects obtained by deaggregating the
         * input list of KinesisEventRecords
         */
        public List<KinesisClientRecord> deaggregate(T inputRecord) throws Exception {
            return new AggregatorUtil().deaggregate(convertType(Arrays.asList(inputRecord)));
        }

        @SuppressWarnings("unchecked")
        private List<KinesisClientRecord> convertType(List<T> inputRecords) throws Exception {
            List<KinesisClientRecord> records = null;

            if (!inputRecords.isEmpty() && inputRecords.get(0) instanceof Record) {
                records = new ArrayList<>();
                for (Record rec : (List<Record>) inputRecords) {
                    records.add(KinesisClientRecord.fromRecord(rec));
                }
            } else {
                if (inputRecords.isEmpty()) {
                    return new ArrayList<>();
                } else {
                    throw new Exception("Input Types must be a Model Records");
                }
            }

            return records;
        }
    }
}
