package com.amazonaws.services.msf.jdbc;

import com.amazonaws.services.msf.domain.StockPrice;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.jdbc.datasource.statements.JdbcQueryStatement;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Query statement for an upsert of a StockPrice, leveraging the PostgreSQL upsert syntax INSERT INTO...ON CONFLICT...DO UPDATE...
 * <p>
 * You can adapt this class to the upsert syntaxes of other databases, such as INSERT INTO...ON DUPLICATE KEY UPDATE... for
 * MySQL, or MERGE INTO... for SQL Server.
 * <p>
 * This class wraps both the parametrized SQL statement to be executed and replacing the parameters in the prepared statement.
 * <p>
 * The table name can be decided when the sink is instantiated.
 */
public class StockPriceUpsertQueryStatement implements JdbcQueryStatement<StockPrice> {

    /**
     * Template for the SQL statement executing the upsert. Depends on the specific RDBMS syntax.
     * The name of the table is parametric (`%s`)
     */
    private static final String UPSERT_QUERY_TEMPLATE =
            "INSERT INTO %s (symbol, price, timestamp) VALUES (?, ?, ?) " +
                    "ON CONFLICT(symbol) DO UPDATE SET price = ?, timestamp = ?";

    private final String sql;
    private final JdbcStatementBuilder<StockPrice> statementBuilder = new JdbcStatementBuilder<StockPrice>() {

        /**
         * Replace the positional parameters in the prepared statement.
         * The implementation of this method depends on the SQL statement which, in turn, is specific of the RDBMS.
         *
         * @param preparedStatement the prepared statement
         * @param stockPrice the StockPrice to upsert
         * @throws SQLException exception thrown replacing parameters
         */
        @Override
        public void accept(PreparedStatement preparedStatement, StockPrice stockPrice) throws SQLException {
            String symbol = stockPrice.getSymbol();
            Timestamp timestamp = Timestamp.from(stockPrice.getTimestamp());
            BigDecimal price = stockPrice.getPrice();

            // Replace the parameters positionally (note that some parameters are repeated in the SQL statement)
            preparedStatement.setString(1, symbol);
            preparedStatement.setBigDecimal(2, price);
            preparedStatement.setTimestamp(3, timestamp);
            preparedStatement.setBigDecimal(4, price);
            preparedStatement.setTimestamp(5, timestamp);
        }
    };

    /**
     * Create an UPSERT stock price query statement for a given table name.
     * Note that, while the values are passed at runtime, the table name must be defined when the sink is instantiated,
     * on start.
     *
     * @param tableName name of the table
     */
    public StockPriceUpsertQueryStatement(String tableName) {
        this.sql = String.format(UPSERT_QUERY_TEMPLATE, tableName);
    }

    /**
     * Returns the SQL of the PreparedStatement
     *
     * @return SQL
     */
    @Override
    public String query() {
        return sql;
    }

    /**
     * Called by the sink for every record to be upserted.
     * The PreparedStatement is mutated, replacing the parameters extracted from the record
     *
     * @param preparedStatement prepared statement
     * @param stockPrice        record
     * @throws SQLException any exception thrown during parameter replacement
     */
    @Override
    public void statement(PreparedStatement preparedStatement, StockPrice stockPrice) throws SQLException {
        statementBuilder.accept(preparedStatement, stockPrice);
    }
}
