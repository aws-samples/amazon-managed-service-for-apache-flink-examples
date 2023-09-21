#!/bin/bash
set -eo pipefail
WINDOW=$1
bucket=$2
build_code=$3

if [ "${WINDOW}" = "TUMBLING" ]
then
    echo "Tumbling window"
  ## For Kafka tumbling window example
  jar_name=tumbling-window-kafka-amazon-app-1.0.jar
  class_name=com.amazonaws.services.msf.windowing.TumblingWindowStreamingJob
  application_name=flink-tumbling-window-kafka
  sink_topic=tumbling-window-sink
  parallelism=1
else
  ## For Kafka sliding window example
  jar_name=sliding-window-kafka-amazon-app-1.0.jar
  class_name=com.amazonaws.services.msf.windowing.SlidingWindowStreamingJobKafkaWithParallelism
  application_name=flink-sliding-window-kafka
  sink_topic=sliding-window-sink
  parallelism=3
fi

template_file=cloudformation/windowing-msk-serverless-cloudformation.yaml
region=ap-south-1
kafka_bootstrap_server="boot-z6eo0mfk.c1.kafka-serverless.ap-south-1.amazonaws.com:9098"
source_topic=windowing-source


if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: deploy.sh <bucket_name> [Y/N default Y]"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

if [ -z "${build_code}" ] || [ "${build_code}" = "Y" ]
then
    echo "Building code"
    mvn -q clean package -DskipTests -Dshade.mainClass=${class_name}
    echo "Copying jar"
    aws s3 cp target/amazon-msf-windowing-app-1.0.jar s3://${bucket}/flink/${jar_name}
fi



echo "Deploying Provisioned"
aws cloudformation deploy --template-file ${template_file} \
     --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND \
     --region ${region} \
     --stack-name ${application_name}  \
     --parameter-overrides ApplicationName=${application_name} \
     FlinkRuntimeEnvironment=FLINK-1_15 \
     CodeBucketArn="arn:aws:s3:::${bucket}" \
     CodeKey=flink/${jar_name} \
     KafkaBootstrapserver=${kafka_bootstrap_server} \
     Parallelism=${parallelism} \
     SinkKafkaTopic=${sink_topic} \
     SourceKafkaTopic=${source_topic}




