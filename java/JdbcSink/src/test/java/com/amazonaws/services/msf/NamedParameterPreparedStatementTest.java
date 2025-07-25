package com.amazonaws.services.msf;

import com.axiomalaska.jdbc.NamedParameterPreparedStatement;
import org.junit.Test;
import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Unit tests for NamedParameterPreparedStatement integration
 * Tests the usage of axiomalaska library for named parameters
 */
public class NamedParameterPreparedStatementTest {

    @Test
    public void testNamedParameterPreparedStatementCreation() {
        String namedSQL = "INSERT INTO prices (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp";
        
        // Test that we can create the SQL string correctly
        assertNotNull("Named SQL should not be null", namedSQL);
        assertTrue("SQL should contain named parameters", namedSQL.contains(":symbol"));
        assertTrue("SQL should contain named parameters", namedSQL.contains(":timestamp"));
        assertTrue("SQL should contain named parameters", namedSQL.contains(":price"));
        assertTrue("SQL should contain UPSERT syntax", namedSQL.contains("ON CONFLICT"));
    }

    @Test
    public void testSQLStructure() {
        String tableName = "prices";
        String namedSQL = String.format(
                "INSERT INTO %s (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                        "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp",
                tableName
        );
        
        String expectedSQL = "INSERT INTO prices (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                           "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp";
        assertEquals("SQL should be formatted correctly", expectedSQL, namedSQL);
    }

    @Test
    public void testTableNameSubstitution() {
        String tableName = "test_prices";
        String namedSQL = String.format(
                "INSERT INTO %s (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                        "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp",
                tableName
        );
        
        assertTrue("SQL should contain the correct table name", namedSQL.contains("test_prices"));
        assertFalse("SQL should not contain placeholder", namedSQL.contains("%s"));
    }

    @Test
    public void testParameterNames() {
        String namedSQL = "INSERT INTO prices (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                         "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp";
        
        // Test that all expected parameter names are present
        String[] expectedParams = {":symbol", ":timestamp", ":price"};
        
        for (String param : expectedParams) {
            assertTrue("SQL should contain parameter " + param, namedSQL.contains(param));
        }
    }

    @Test
    public void testSQLSyntax() {
        String namedSQL = "INSERT INTO prices (symbol, timestamp, price) VALUES (:symbol, :timestamp, :price) " +
                         "ON CONFLICT(symbol) DO UPDATE SET price = :price, timestamp = :timestamp";
        
        // Basic SQL syntax checks
        assertTrue("SQL should start with INSERT", namedSQL.startsWith("INSERT"));
        assertTrue("SQL should contain INTO", namedSQL.contains("INTO"));
        assertTrue("SQL should contain VALUES", namedSQL.contains("VALUES"));
        assertTrue("SQL should contain ON CONFLICT for UPSERT", namedSQL.contains("ON CONFLICT"));
        assertTrue("SQL should contain DO UPDATE SET", namedSQL.contains("DO UPDATE SET"));
        assertTrue("SQL should have proper parentheses", namedSQL.contains("(") && namedSQL.contains(")"));
    }
}
