#!/bin/bash

# This script returns the status of all tasks (called "vertices" in the API) of the Flink job.
# The script expects the application name as only parameter. It assumes the AWS default profile
# and Region are correctly set for the Managed Flink application.

# Validate parameters
if [ $# -eq 0 ]; then
    echo "Error: Application name required"
    echo "Usage: $0 <application-name>"
    exit 1
fi

# Generate the pre-signed URL for the application
output=$(aws kinesisanalyticsv2 create-application-presigned-url \
    --application-name "$1" \
    --url-type FLINK_DASHBOARD_URL 2>&1)

# Check if the output contains ResourceInUseException. It will happen when the application
# is not RUNNING, and the pre-signed URL is not available
if echo "$output" | grep -q "An error occurred (ResourceInUseException)"; then
    echo "UNKNOWN"
    exit 0
fi

# Parse the pre-signed URL
presigned_url=$(echo "$output" | jq -r '.AuthorizedUrl')
base_url=$(echo "$presigned_url" | grep -o 'https://[^/]*\.amazonaws\.com/flinkdashboard')
auth_token=$(echo "$presigned_url" | grep -o 'authToken=[^&]*' | cut -d'=' -f2)

# Jobs endpoint URL
jobs_url="${base_url}/jobs?authToken=${auth_token}"

# GET jobs status. Extract the Job ID assuming a single job is running
jobs_response=$(wget -qO- "${jobs_url}")
job_id=$(echo "$jobs_response" | jq -r '.jobs[0].id')

# Job detail endpoint URL
job_details_url="${base_url}/jobs/${job_id}?authToken=${auth_token}"

# GET Job details
job_details=$(wget -qO- "${job_details_url}")

# Extract statuses of all vertices and join with space
vertices_statuses=$(echo "$job_details" | jq -r '.vertices[].status')

echo "$vertices_statuses"
