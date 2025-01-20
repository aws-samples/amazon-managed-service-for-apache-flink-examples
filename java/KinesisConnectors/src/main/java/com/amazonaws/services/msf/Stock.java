package com.amazonaws.services.msf;

public class Stock {
    public String event_time;
    public String ticker;
    public float price;

    public Stock() {}

    public Stock mutateTicker(String ticker) {
        this.ticker = ticker;
        return this;
    }

    @Override
    public String toString() {
        return "Stock{" +
                "event_time='" + event_time + '\'' +
                ", ticker='" + ticker + '\'' +
                ", price=" + price +
                '}';
    }
}
