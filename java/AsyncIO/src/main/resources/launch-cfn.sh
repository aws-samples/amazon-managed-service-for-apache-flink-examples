aws cloudformation create-stack \
    --stack-name RandomResponseStack \
    --template-body file:///Users/jdber/Documents/workspace/blog-datapipeline-exceptions/retries/src/main/resources/lambda-cloudformation.yaml \
    --capabilities CAPABILITY_IAM
