package com.amazonaws.services.msf.domain;

public class StockPrice {
    private String timestamp;
    private String symbol;
    private Float price;
    private Integer volumes;

    public StockPrice() {
    }

    public StockPrice(String timestamp, String symbol, Float price, Integer volumes) {
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.price = price;
        this.volumes = volumes;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public Integer getVolumes() {
        return volumes;
    }

    public void setVolumes(Integer volumes) {
        this.volumes = volumes;
    }

    @Override
    public String toString() {
        return "com.amazonaws.services.msf.pojo.StockPrice{" +
                "timestamp='" + timestamp + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", volumes=" + volumes +
                '}';
    }
}