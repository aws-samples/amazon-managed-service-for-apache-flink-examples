#!/bin/bash
bucket=$1

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: build-kafka-sample.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

echo "Windowing type is tumbling"
class_name=com.amazonaws.services.msf.windowing.kafka.TumblingWindowStreamingJob
jar_name=kafka-windowing-tumbling-1.0.jar

echo "main class is ${class_name}"
echo "jar will be uploaded to s3://${bucket}/flink/${jar_name}"
echo "Building code"
mvn -q clean package -DskipTests -Dshade.mainClass=${class_name}
echo "Copying jar"
aws s3 cp target/amazon-msf-windowing-app-1.0.jar s3://${bucket}/flink/${jar_name}

echo "Done"




