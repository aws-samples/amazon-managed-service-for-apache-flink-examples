#!/bin/bash

# Test script for JdbcSink example with JavaFaker
set -e

echo "🚀 Starting JdbcSink Example Test (JavaFaker Edition)"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "✅ Docker is running"

# Navigate to docker directory
cd docker

# Start PostgreSQL
echo "🐘 Starting PostgreSQL..."
docker-compose up -d

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
timeout=60
counter=0
while ! docker-compose exec -T postgres pg_isready -U flinkuser -d testdb > /dev/null 2>&1; do
    if [ $counter -ge $timeout ]; then
        echo "❌ PostgreSQL failed to start within $timeout seconds"
        docker-compose logs postgres
        exit 1
    fi
    sleep 1
    counter=$((counter + 1))
done

echo "✅ PostgreSQL is ready"

# Check if table was created
echo "🔍 Checking if users table was created..."
if docker-compose exec -T postgres psql -U flinkuser -d testdb -c "\dt" | grep -q users; then
    echo "✅ Users table exists"
else
    echo "❌ Users table was not created"
    exit 1
fi

# Show table structure
echo "📋 Table structure:"
docker-compose exec -T postgres psql -U flinkuser -d testdb -c "\d users"

# Show initial data
echo "📊 Initial data:"
docker-compose exec -T postgres psql -U flinkuser -d testdb -c "SELECT * FROM users;"

# Go back to project root
cd ..

# Build the project
echo "🔨 Building the project..."
mvn clean package -q

if [ $? -eq 0 ]; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    exit 1
fi

echo ""
echo "🎉 Setup complete! You can now:"
echo "   1. Run the Flink job: mvn exec:java -Dexec.mainClass=\"com.amazonaws.services.msf.JdbcSinkJob\""
echo "   2. Monitor data: ./monitor.sh"
echo "   3. Stop PostgreSQL: docker-compose -f docker/docker-compose.yml down"
echo ""
echo "📝 Database connection details:"
echo "   URL: jdbc:postgresql://localhost:5432/testdb"
echo "   Username: flinkuser"
echo "   Password: flinkpassword"
echo ""
echo "🎭 This example uses JavaFaker to generate realistic fake user data!"
