#!/bin/bash
bucket=$1
build_code=$2

jar_name=kafka-connectors-1.0.jar

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: deploy.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

if [ -z "${build_code}" ] || [ "${build_code}" = "Y" ]
then
    echo "Building code"
    mvn -q  clean package -DskipTests
    echo "Copying jar"
    aws s3 cp target/${jar_name} s3://${bucket}/flink/
fi




