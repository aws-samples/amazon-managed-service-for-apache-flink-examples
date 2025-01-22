package com.amazonaws.services.msf;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

public class Stock {
    // This annotation as well as the associated jackson2 import is needed to correctly map the JSON input key to the
    // appropriate POJO property name to ensure event_time isn't missed in serialization and deserialization
    @JsonProperty("event_time")
    private String eventTime;
    private String ticker;
    private float price;

    public Stock() {}

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public float getPrice() {
        return price;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "event_time='" + eventTime + '\'' +
                ", ticker='" + ticker + '\'' +
                ", price=" + price +
                '}';
    }
}
