package com.amazonaws.services.msf;

import java.time.Instant;

public class ChangeEvent {

    private Instant timestamp;
    private Type eventType;
    private DdbTableItem oldItem;
    private DdbTableItem newItem;

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Type getEventType() {
        return eventType;
    }

    public void setEventType(Type eventType) {
        this.eventType = eventType;
    }

    public DdbTableItem getOldItem() {
        return oldItem;
    }

    public void setOldItem(DdbTableItem oldItem) {
        this.oldItem = oldItem;
    }

    public DdbTableItem getNewItem() {
        return newItem;
    }

    public void setNewItem(DdbTableItem newItem) {
        this.newItem = newItem;
    }

    @Override
    public String toString() {
        return "ChangeEvent{" +
                "timestamp=" + timestamp +
                ", eventType=" + eventType +
                ", oldItem=" + oldItem +
                ", newItem=" + newItem +
                '}';
    }

    public enum Type {
        INSERT,
        MODIFY,
        REMOVE
    }
}
