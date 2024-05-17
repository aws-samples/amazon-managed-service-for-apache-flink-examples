package com.amazonaws.services.msf.windowing;

import java.sql.Timestamp;

public class StockPrice {
    private Timestamp eventTime;
    private String ticker;
    private Double price;


    public StockPrice() {
    }

    public StockPrice(Timestamp eventTime, String ticker, Double price) {
        this.eventTime = eventTime;
        this.ticker = ticker;
        this.price = price;
    }

    public Timestamp getEventTime() {
        return eventTime;
    }

    public void setEventTime(Timestamp eventTime) {
        this.eventTime = eventTime;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "StockPrice{" +
                "eventTime=" + eventTime +
                ", ticker='" + ticker + '\'' +
                ", price=" + price +
                '}';
    }
}
