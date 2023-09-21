#!/bin/bash
set -eo pipefail
bucket=$1
build_code=$2


## For Kafka sliding window example
jar_name=sliding-window-kinesis-amazon-app-1.0.jar
class_name=com.amazonaws.services.msf.windowing.SlidingWindowStreamingJobWithParallelism
application_name=flink-sliding-window-kafka
sink_topic=sliding-window-sink
parallelism=3

template_file=cloudformation/windowing-kinesis-cloudformation.yaml
input_region=ap-south-1
input_stream="stream-input"
output_region=ap-south-1
output_stream="stream-output"



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
     InputRegion=${input_region} \
     InputStream=${input_stream} \
     OutputRegion=${output_region} \
     OutputStream=${output_stream}




