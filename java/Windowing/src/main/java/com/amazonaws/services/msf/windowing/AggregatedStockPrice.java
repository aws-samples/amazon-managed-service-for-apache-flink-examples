package com.amazonaws.services.msf.windowing;

import org.apache.flink.streaming.api.windowing.windows.TimeWindow;

public class AggregatedStockPrice {
    private TimeWindow timeWindow;
    private String ticker;
    private Double minPrice;


    public AggregatedStockPrice() {
    }

    public AggregatedStockPrice(TimeWindow timeWindow, String ticker, Double minPrice) {
        this.timeWindow = timeWindow;
        this.ticker = ticker;
        this.minPrice = minPrice;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public void setTimeWindow(TimeWindow timeWindow) {
        this.timeWindow = timeWindow;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Double minPrice) {
        this.minPrice = minPrice;
    }

    @Override
    public String toString() {
        return "StockPrice{" +
                "timeWindow=" + timeWindow +
                ", ticker='" + ticker + '\'' +
                ", minPrice=" + minPrice +
                '}';
    }
}
