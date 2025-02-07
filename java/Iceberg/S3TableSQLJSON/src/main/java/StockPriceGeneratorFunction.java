import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import java.sql.Timestamp;

/**
 * Generates random stock price data for simulation purposes.
 */
public class StockPriceGeneratorFunction implements GeneratorFunction<Long, StockPrice> {

    private static final String[] STOCK_SYMBOLS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};
    private static final double MAX_PRICE = 100.0;
    private static final double MIN_PRICE = 1.0;

    @Override
    public StockPrice map(Long sequence) throws Exception {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        String randomSymbol = STOCK_SYMBOLS[RandomUtils.nextInt(0, STOCK_SYMBOLS.length)];
        double randomPrice = RandomUtils.nextDouble(MIN_PRICE, MAX_PRICE);

        return new StockPrice(currentTimestamp, randomSymbol, randomPrice);
    }
}
