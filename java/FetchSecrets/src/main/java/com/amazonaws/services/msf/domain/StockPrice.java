package com.amazonaws.services.msf.domain;

public class StockPrice {
    private String symbol;
    private String timestamp;
    private double price;

    public StockPrice() {}

    public StockPrice(String symbol, String timestamp, double price) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.price = price;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
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
                "symbol='" + symbol + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", price=" + price +
                '}';
    }
}
