#!/bin/bash
bucket=$1

jar_name=kafka-connectors-1.0.jar
stack_name=flink-kafka-sample
application_name=flink-kafka-sample
template_file=cloudformation/msf-msk-iam-auth.yaml


## Region and Network configuration
region=ap-south-1
SecurityGroup=sg-28d5f054
SubnetOne=subnet-08710af059f886114
SubnetTwo=subnet-7d90f906
SubnetThree=subnet-02e1e451e78007768

## MSK configuration
kafka_bootstrap_server="boot-z6eo0mfk.c1.kafka-serverless.ap-south-1.amazonaws.com:9098"
source_topic="flink-kafka-sample-source"
sink_topic="flink-kafka-sample-sink"

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: deploy.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
    ## Check if S3 file exists
    aws s3 ls s3://${bucket}/flink/${jar_name}
    if [ $? -ne 0 ]
    then
        echo "s3://${bucket}/flink/${jar_name} does not exist"
        echo "Please execute: build.sh <bucket_name>"
        echo "Then try again"
        exit 1
    fi
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
     SecurityGroup=${SecurityGroup} \
     SubnetOne=${SubnetOne} \
     SubnetTwo=${SubnetTwo} \
     SubnetThree=${SubnetThree} \
     KafkaBootstrapserver=${kafka_bootstrap_server} \
     SourceKafkaTopic=${source_topic} \
     SinkKafkaTopic=${sink_topic}



echo "Deployment completed"
aws cloudformation describe-stacks --stack-name ${stack_name} --region ${region}  --no-cli-pager




