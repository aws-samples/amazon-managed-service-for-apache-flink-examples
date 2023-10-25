#!/bin/bash
bucket=$1

template_file=cloudformation/msf-kinesis-stream-windowing.yaml

echo "Tumbling window"
## For Kafka sliding window example
jar_name=amazon-msf-windowing-tumbling-app-1.0.jar
application_name=flink-kinesis-windowing-tumbling
parallelism=3

if [ -z "${bucket}" ]
then
    echo "Bucket name is required"
    echo "Usage: deploy.sh <bucket_name>"
    exit 1
else
    echo "Bucket name is ${bucket}"
fi

aws s3 ls s3://${bucket}/flink/${jar_name}
if [ $? -ne 0 ]
then
    echo "s3://${bucket}/flink/${jar_name} does not exist"
    echo "Please execute: build.sh <bucket_name>"
    echo "Then try again"
    exit 1
fi

## Region and Network configuration
region=ap-south-1
SecurityGroup=sg-28d5f054
SubnetOne=subnet-08710af059f886114
SubnetTwo=subnet-7d90f906
SubnetThree=subnet-02e1e451e78007768

## Kinesis configuration
input_stream="stream-input"
output_stream="stream-windowing-tumbling-output"




echo "Deploying Provisioned"
aws cloudformation deploy --template-file ${template_file} \
     --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND \
     --region ${region} \
     --stack-name ${application_name}  \
     --parameter-overrides ApplicationName=${application_name} \
     FlinkRuntimeEnvironment=FLINK-1_15 \
     CodeBucketArn="arn:aws:s3:::${bucket}" \
     CodeKey=flink/${jar_name} \
     SecurityGroup=${SecurityGroup} \
     SubnetOne=${SubnetOne} \
     SubnetTwo=${SubnetTwo} \
     SubnetThree=${SubnetThree} \
     Parallelism=${parallelism} \
     InputStreamRegion=${region} \
     InputStreamName=${input_stream} \
     OutputStreamRegion=${region} \
     OutputStreamName=${output_stream}
