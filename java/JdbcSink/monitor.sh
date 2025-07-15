#!/bin/bash

# Monitoring script for JdbcSink example with JavaFaker data
set -e

echo "📊 JdbcSink Monitoring Dashboard (JavaFaker Edition)"
echo "===================================================="

# Function to execute SQL and display results
execute_sql() {
    local query="$1"
    local description="$2"
    
    echo ""
    echo "🔍 $description"
    echo "---"
    docker-compose -f docker/docker-compose.yml exec -T postgres psql -U flinkuser -d testdb -c "$query"
}

# Check if PostgreSQL is running
if ! docker-compose -f docker/docker-compose.yml ps postgres | grep -q "Up"; then
    echo "❌ PostgreSQL is not running. Please start it first:"
    echo "   cd docker && docker-compose up -d"
    exit 1
fi

# Display current timestamp
echo "⏰ Current time: $(date)"

# Total record count
execute_sql "SELECT COUNT(*) as total_records FROM users;" "Total Records"

# Recent records with key information
execute_sql "SELECT user_id, first_name, last_name, email, city, country, job_title, company FROM users ORDER BY created_at DESC LIMIT 5;" "Latest 5 Records"

# Records per minute (last 10 minutes)
execute_sql "
SELECT 
    DATE_TRUNC('minute', created_at) as minute,
    COUNT(*) as records_per_minute
FROM users 
WHERE created_at >= NOW() - INTERVAL '10 minutes'
GROUP BY DATE_TRUNC('minute', created_at)
ORDER BY minute DESC
LIMIT 10;" "Records per Minute (Last 10 minutes)"

# Top countries
execute_sql "
SELECT 
    country,
    COUNT(*) as count
FROM users 
GROUP BY country
ORDER BY count DESC
LIMIT 10;" "Top Countries"

# Top cities
execute_sql "
SELECT 
    city,
    country,
    COUNT(*) as count
FROM users 
GROUP BY city, country
ORDER BY count DESC
LIMIT 10;" "Top Cities"

# Top companies
execute_sql "
SELECT 
    company,
    COUNT(*) as count
FROM users 
GROUP BY company
ORDER BY count DESC
LIMIT 10;" "Top Companies"

# Top job titles
execute_sql "
SELECT 
    job_title,
    COUNT(*) as count
FROM users 
GROUP BY job_title
ORDER BY count DESC
LIMIT 10;" "Top Job Titles"

# Age distribution (approximate)
execute_sql "
SELECT 
    CASE 
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 18 AND 25 THEN '18-25'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 26 AND 35 THEN '26-35'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 36 AND 45 THEN '36-45'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 46 AND 55 THEN '46-55'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) BETWEEN 56 AND 65 THEN '56-65'
        ELSE '65+'
    END as age_group,
    COUNT(*) as count
FROM users 
WHERE date_of_birth IS NOT NULL
GROUP BY age_group
ORDER BY age_group;" "Age Distribution"

# Email domains
execute_sql "
SELECT 
    SPLIT_PART(email, '@', 2) as domain,
    COUNT(*) as count
FROM users 
GROUP BY SPLIT_PART(email, '@', 2)
ORDER BY count DESC
LIMIT 10;" "Email Domains Distribution"

# First and last record timestamps
execute_sql "
SELECT 
    MIN(created_at) as first_record,
    MAX(created_at) as last_record,
    MAX(created_at) - MIN(created_at) as duration
FROM users;" "Time Range"

# Data quality check
execute_sql "
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT email) as unique_emails,
    COUNT(CASE WHEN phone_number IS NOT NULL THEN 1 END) as records_with_phone,
    COUNT(CASE WHEN address IS NOT NULL THEN 1 END) as records_with_address,
    COUNT(CASE WHEN date_of_birth IS NOT NULL THEN 1 END) as records_with_dob,
    ROUND(AVG(EXTRACT(YEAR FROM AGE(date_of_birth))), 1) as avg_age
FROM users;" "Data Quality Summary"

echo ""
echo "🔄 To refresh this dashboard, run: ./monitor.sh"
echo "🛑 To stop monitoring: Ctrl+C"
echo "📝 To view logs: docker-compose -f docker/docker-compose.yml logs -f postgres"
echo "🎭 Powered by JavaFaker for realistic fake data generation"
