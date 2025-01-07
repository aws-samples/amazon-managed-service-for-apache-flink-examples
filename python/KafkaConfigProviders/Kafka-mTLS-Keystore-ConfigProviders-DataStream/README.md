## Using MSK Config Providers to set up Kafka mTLS authentication, in PyFlink DataStream API

* Flink version: 1.20
* Flink API: DataStream API
* Flink Connectors: Kafka 
* Language: Python

TBD

### Required IAM permissions

The application should have the following permissions:
* Read from the S3 bucket containing the keystore and truststore
* Read the secret containing the password of the keystore and truststre

Example policy

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "GetJks",
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:GetObjectVersion"
            ],
            "Resource": [
                "arn:aws:s3:::mybucket/msk-tls/kafka.client.keystore.jks",
                "arn:aws:s3:::mybucket/msk-tls/kafka.client.truststore.jks"
            ]
        },
        {
            "Sid": "ReadJksPassword",
            "Effect": "Allow",
            "Action": [
                "secretsmanager:GetSecretValue"
            ],
            "Resource": [
                "arn:aws:secretsmanager:us-east-1:1234567890132:secret:SSL_KEYSTORE_PASS-??????"
            ]
        }
    ]
}
```