## Deploying using CloudFormation to Amazon Managed Service for Apache Flink
### Pre-requisite
1. Kinesis stream and S3 bucket.
2. AWS user credential using which you can create CloudFormation stack from console or CLI.

### Build and deployment
1. The steps below create stack using `./cloudformation/msf-deploy.yaml`.
2. The script `deploy.sh` creates the stack using AWS CLI. Ensure that AWS CLI is configured and your user has permissions to create CloudFormation stack.
3. Alternatively you can deploy from console using `./cloudformation/msf-deploy.yaml` and pass required parameters.
4. Edit `deploy.sh` to modify  "Region configuration" . Modify following configurations -
* region= Deployment region

5. Edit `deploy.sh` to modify "Kinesis and S3 Sink configuration". Modify following configurations -
* input_stream= Input Kinesis stream name.
* s3_bucket_name= S3 Bucket name
* s3_file_path = S3 folder path. Ex. flink/msf
  Ensure that source stream and sink bucket  are created.

6. To build code, execute the script below which will build the jar and upload the jar to S3 at s3://BUCKET_NAME/flink/flink-kds-s3.jar.
```shell
./build.sh <BUCKET_NAME>
```
7. Run `deploy.sh` to deploy the CloudFormation template . Refer the sample CloudFormation template at `./cloudformation/msf-deploy.yaml` .
   The CloudFormation needs the jar to be there at s3://BUCKET_NAME/flink/flink-kds-s3.jar.

```
./deploy.sh <BUCKET_NAME> 
```
8. The template creates following resources -
* Flink application with application name defined by application_name in deploy.sh.
* CloudWatch log group with name - /aws/amazon-msf/${application_name}
* CloudWatch log stream under the log group created above by name amazon-msf-log-stream.
* IAM execution role for Flink application.
* IAM managed policy for permission. 
