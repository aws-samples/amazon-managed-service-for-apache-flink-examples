#!/bin/bash
bucket=$1

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: build-kinesis-sample.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

echo "Windowing type is sliding"
class_name=com.amazonaws.services.msf.windowing.kinesis.SlidingWindowStreamingJobWithParallelism
jar_name=kinesis-windowing-sliding-1.0.jar

echo "main class is ${class_name}"
echo "jar will be uploaded to s3://${bucket}/flink/${jar_name}"
echo "Building code"
mvn -q clean package -DskipTests -Dshade.mainClass=${class_name}
echo "Copying jar"
aws s3 cp target/amazon-msf-windowing-app-1.0.jar s3://${bucket}/flink/${jar_name}

echo "Done"




