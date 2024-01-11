#!/bin/bash
bucket=$1

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: build.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

jar_name=flink-kds-s3-parquet.jar

echo "jar will be uploaded to s3://${bucket}/flink/${jar_name}"
echo "Building code"
mvn -q clean package -DskipTests
echo "Copying jar"
aws s3 cp target/${jar_name} s3://${bucket}/flink/${jar_name}

echo "Done"




