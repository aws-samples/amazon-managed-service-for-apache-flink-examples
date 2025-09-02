package com.amazonaws.services.msf;

/**
 * Option type is a generic container to hold deserialized records.
 * It can be useful to introduce fields into this type to handle partitioning
 * strategies and event time extraction. However, for those scenarios to work
 * all subjects should have a standard set of fields.
 */
class Option {
    private Object value;

    public Option() {
    }

    public Option(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return this.value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
