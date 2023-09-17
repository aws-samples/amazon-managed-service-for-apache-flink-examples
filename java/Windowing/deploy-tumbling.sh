#!/bin/bash
set -eo pipefail
stack_name=flink-tumbling-window-app
application_name=flink-tumbling-window-app
template_file=windowing-kafka-cloudformation.yaml
region=ap-south-1
bootstrap_server="boot-z6eo0mfk.c1.kafka-serverless.ap-south-1.amazonaws.com:9098"

echo "Building code"
mvn -q clean package -DskipTests
echo "Copying jar"
aws s3 cp target/amazon-msf-windowing-app-1.0.jar s3://aksh-code-binaries/flink/

echo "Deploying Provisioned"
aws cloudformation deploy --template-file ${template_file} --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND \
     --region ${region} \
     --stack-name ${stack_name}  \
     --parameter-overrides ApplicationName=${application_name} \
     FlinkRuntimeEnvironment=FLINK-1_15 \
     CodeBucketArn="arn:aws:s3:::aksh-code-binaries" \
     CodeKey=flink/amazon-msf-windowing-app-1.0.jar \
     KafkaBootstrapserver=${bootstrap_server}


