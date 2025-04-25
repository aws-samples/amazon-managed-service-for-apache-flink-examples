package com.amazonaws.services.kds.producer.model;

public class StockPrice {
    private String eventTime;
    private String ticker;
    private double price;

    public StockPrice(String eventTime, String ticker, double price) {
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
