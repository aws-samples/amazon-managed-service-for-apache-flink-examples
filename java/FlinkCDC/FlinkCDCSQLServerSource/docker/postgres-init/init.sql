-- Create customer table
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(40),
    middle_initial VARCHAR(40),
    last_name VARCHAR(40),
    email VARCHAR(50),
    _source_updated_at TIMESTAMP,
    _change_processed_at TIMESTAMP
);
