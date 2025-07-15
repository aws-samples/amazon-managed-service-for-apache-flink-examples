-- Create users table for JDBC sink with comprehensive user information
CREATE TABLE users (
    user_id INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    address VARCHAR(200),
    city VARCHAR(50),
    country VARCHAR(50),
    job_title VARCHAR(100),
    company VARCHAR(100),
    date_of_birth DATE,
    created_at TIMESTAMP NOT NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_name ON users(first_name, last_name);
CREATE INDEX idx_users_location ON users(city, country);
CREATE INDEX idx_users_company ON users(company);
CREATE INDEX idx_users_job_title ON users(job_title);

-- Insert some sample data for testing
INSERT INTO users (user_id, first_name, last_name, email, phone_number, address, city, country, job_title, company, date_of_birth, created_at) VALUES
(0, 'Sample', 'User', 'sample.user@example.com', '+1-555-123-4567', '123 Main St', 'Sample City', 'Sample Country', 'Software Engineer', 'Sample Corp', '1990-01-01', NOW());

-- Display table structure
\d users;

-- Display sample data
SELECT * FROM users;

-- Show table statistics
SELECT 
    schemaname,
    tablename,
    attname as column_name,
    n_distinct,
    correlation
FROM pg_stats 
WHERE tablename = 'users';
