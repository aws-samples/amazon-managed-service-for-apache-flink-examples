package com.amazonaws.services.msf.domain;


import java.util.Objects;

public class StockPrice {
    // IMPORTANT: you must use the shaded Jackson annotations (under org.apache.flink.shaded.jackson2.com.fasterxml...)
    // otherwise JsonSerializationSchema, which is also shaded, will not honor it.
    @org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty("event_time")
    private String eventTime;
    private String ticker;
    private float price;

    public StockPrice() {}

    public StockPrice(String eventTime, String ticker, float price) {
        this.eventTime = eventTime;
        this.ticker = ticker;
        this.price = price;
    }

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockPrice stock = (StockPrice) o;
        return Float.compare(stock.price, price) == 0 &&
                Objects.equals(eventTime, stock.eventTime) &&
                Objects.equals(ticker, stock.ticker);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTime, ticker, price);
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
