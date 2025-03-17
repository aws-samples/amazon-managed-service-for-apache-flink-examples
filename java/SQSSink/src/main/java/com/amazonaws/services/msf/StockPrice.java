package com.amazonaws.services.msf;

import java.sql.Timestamp;

public class StockPrice {
    private Timestamp eventTime;
    private String ticker;
    private double price;


    public StockPrice() {
    }

    public StockPrice(Timestamp eventTime, String ticker, double price) {
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
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