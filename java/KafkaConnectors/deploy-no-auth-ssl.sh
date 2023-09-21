#!/bin/bash
set -eo pipefail
bucket=$1
build_code=$2

jar_name=kafka-connectors-1.0.jar
stack_name=flink-kafka-no-auth-ssl
application_name=flink-kafka-no-auth-ssl
template_file=cloudformation/template-no-auth-ssl.yaml
region=ap-south-1

source_kafka_bootstrap_server="b-3.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094,b-1.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094,b-2.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094"
source_topic=flink-kafka-sample-src
sink_kafka_bootstrap_server="b-3.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094,b-1.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094,b-2.mskprovisioned2.xe3lni.c3.kafka.ap-south-1.amazonaws.com:9094"
sink_topic=flink-kafka-sample-snk

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
    mvn -q  clean package -DskipTests
    echo "Copying jar"
    aws s3 cp target/${jar_name} s3://${bucket}/flink/
fi



echo "Deploying Provisioned"
aws cloudformation deploy --template-file ${template_file} \
     --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND \
     --region ${region} \
     --stack-name ${stack_name}  \
     --parameter-overrides ApplicationName=${application_name} \
     FlinkRuntimeEnvironment=FLINK-1_15 \
     CodeBucketArn="arn:aws:s3:::${bucket}" \
     CodeKey=flink/${jar_name} \
     Parallelism=3 \
     SinkKafkaBootstrapserver=${sink_kafka_bootstrap_server} \
     SinkKafkaTopic=${sink_topic} \
     SourceKafkaBootstrapserver=${source_kafka_bootstrap_server} \
     SourceKafkaTopic=${source_topic}

echo "Deployment completed"
aws cloudformation describe-stacks --stack-name ${stack_name} --region ${region}  --no-cli-pager




