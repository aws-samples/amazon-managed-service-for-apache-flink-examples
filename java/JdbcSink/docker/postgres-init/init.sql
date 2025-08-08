-- Create prices table for JDBC sink
CREATE TABLE prices (
    symbol VARCHAR(10) PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    price DECIMAL(10,2) NOT NULL
);

-- Insert some sample data for testing
INSERT INTO prices (symbol, timestamp, price) VALUES
('AAPL', NOW(), 150.25);

-- Display table structure
\d prices;

-- Display sample data
SELECT * FROM prices;

-- Show table statistics
SELECT 
    schemaname,
    tablename,
    attname as column_name,
    n_distinct,
    correlation
FROM pg_stats 
WHERE tablename = 'prices';
