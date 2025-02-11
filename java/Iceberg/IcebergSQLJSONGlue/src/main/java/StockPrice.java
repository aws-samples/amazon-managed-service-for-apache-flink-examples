
import java.sql.Timestamp;

public class StockPrice {
    private Timestamp eventtime;
    private String ticker;
    private Double price;


    public StockPrice() {
    }

    public StockPrice(Timestamp eventtime, String ticker, Double price) {
        this.eventtime = eventtime;
        this.ticker = ticker;
        this.price = price;
    }

    public Timestamp getEventtime() {
        return eventtime;
    }

    public void setEventtime(Timestamp eventtime) {
        this.eventtime = eventtime;
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
                "eventtime=" + eventtime +
                ", ticker='" + ticker + '\'' +
                ", price=" + price +
                '}';
    }
}
