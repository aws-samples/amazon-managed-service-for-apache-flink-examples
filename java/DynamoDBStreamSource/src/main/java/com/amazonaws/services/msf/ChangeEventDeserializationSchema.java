package com.amazonaws.services.msf;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.dynamodb.source.serialization.DynamoDbStreamsDeserializationSchema;
import org.apache.flink.util.Collector;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.Record;

import java.io.IOException;

public class ChangeEventDeserializationSchema implements DynamoDbStreamsDeserializationSchema<ChangeEvent> {

    private static final TableSchema<DdbTableItem> TABLE_ITEM_TABLE_SCHEMA = TableSchema.fromBean(DdbTableItem.class);

    @Override
    public void deserialize(Record record, String s, String s1, Collector<ChangeEvent> collector) throws IOException {
        ChangeEvent changeEvent = new ChangeEvent();
        changeEvent.setTimestamp(record.dynamodb().approximateCreationDateTime());

        switch (record.eventName()) {
            case INSERT:
                changeEvent.setEventType(ChangeEvent.Type.INSERT);
                changeEvent.setNewItem(TABLE_ITEM_TABLE_SCHEMA.mapToItem(record.dynamodb().newImage()));
                break;
            case MODIFY:
                changeEvent.setEventType(ChangeEvent.Type.MODIFY);
                changeEvent.setOldItem(TABLE_ITEM_TABLE_SCHEMA.mapToItem(record.dynamodb().oldImage()));
                changeEvent.setNewItem(TABLE_ITEM_TABLE_SCHEMA.mapToItem(record.dynamodb().newImage()));
                break;
            case REMOVE:
                changeEvent.setEventType(ChangeEvent.Type.REMOVE);
                changeEvent.setOldItem(TABLE_ITEM_TABLE_SCHEMA.mapToItem(record.dynamodb().oldImage()));
                break;
            default:
                throw new IllegalStateException("Unknown event name " + record.eventName());
        }
        collector.collect(changeEvent);
    }

    @Override
    public TypeInformation<ChangeEvent> getProducedType() {
        return TypeInformation.of(ChangeEvent.class);
    }
}
