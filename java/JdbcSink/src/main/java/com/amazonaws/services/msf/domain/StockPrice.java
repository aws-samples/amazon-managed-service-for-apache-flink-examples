package com.amazonaws.services.msf.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class StockPrice {
    private String symbol;
    
    private Instant timestamp;
    
    private BigDecimal price;

    public StockPrice() {}

    public StockPrice(String symbol, Instant timestamp, BigDecimal price) {
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockPrice that = (StockPrice) o;
        return Objects.equals(symbol, that.symbol) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(price, that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, timestamp, price);
    }

    @Override
    public String toString() {
        return "StockPrice{" +
                "symbol='" + symbol + '\'' +
                ", timestamp='" + timestamp.toString() + '\'' +
                ", price=" + price +
                '}';
    }
}
