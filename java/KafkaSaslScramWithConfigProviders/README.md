## Example showing configuring Kafka connector for SASL/SCRAM using MSK Config Providers

* Flink version: 1.19
* Flink API: DataStream API
* Language: Java (11)
* Connectors: Kafka

This example illustrates how to set up a Kafka connector to MSK using SASL/SCRAM authentication, fetching the credentials
at runtime directly from SecretsManager.

In this sample application we demonstrate how to configure a KafkaSink. 
The process of configuring a KafkaSource SASL/SCRAM is identical.

> TBD complete description of the approach

### TrustStore for SASL_SSL

For SASL_SSL authentication, the Kafka client requires a TrustStore explicitly defined, even if no custom certificate is actually required.
The client does not fall back to the JVM TrustStore if `ssl.truststore.location` is not explicitly defined.

For this reason, you need to upload a TrustStore into an S3 bucket. The application downloads the file on start using Kafka Configuration Providers,
and uses it to set up SASL_SSL.

Note that this can be a copy of the JVM TrustStore (with its default "changit" password), because no additional custom certificate
is actually required.

This application assumes the TrustStore password is the default "changit". If this is not the case, you can use an additional secret
in SecretsManager, to store the password.

### SecretsManager VPC Endpoint

Create a VPC Endpoint for SecretsManager in the VPC that hosts the Managed Flink application.

### Secret encryption key

MSK requires encrypting the SASL/SCRAM secret with a custom KMS key.
This key is not directly referred in the application, but the application must have the permission to use that key to decrypt the secret.

### IAM Permissions

The following permissions must be added to the Managed Service for Apache Flink application IAM Role,
to allow fetching the truststore from S3 and the SASL/SCRAM credentials from Secrets manager:

1. Read the secret from Secret Manager:
   * Actions: "secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret", "secretsmanager:DescribeSecret", "secretsmanager:GetResourcePolicy", "secretsmanager:ListSecretVersionIds"
   * Resource: "arn:aws:secretsmanager:<region>:<account-id>:secret:<secret-name>"
2. Fetch the TrustStore from S3
   * Actions: "s3:GetObject"
   * Resource: "arn:aws:s3:::<bucket-name>/<object-key>"
3. Decrypt the secret stored in SecretsManager - This is the KMS key used to encrypt the secret that contains  SASL/SCRAM credentials
   * Action: "kms:Decrypt"
   * Resource: "arn:aws:kms:<region>:<account-id>:key/<key-id>"

### Application configuration parameters

| Group ID | Key                                 | Description                                                                              |
|----------|-------------------------------------|------------------------------------------------------------------------------------------|
| `Output0` | `bootstrap.servers`                 | Kafka/MSK bootstrap servers for SASL/SCRAM authentication (typically with port `9096`    |
| `Output0` | `topic`                             | Name of the output topic                                                                 |
| `Output0` | `bucket.region`                     | Region of the S3 bucket containing the TrustStore                                        |
| `Output0` | `truststore.bucket`                 | Name of the S3 bucket containing the TrustStore  (without `s3://` prefix)                |
| `Output0` | `truststore.path`                   | Path to the TrustStore, in the S3 bucket (without trailing `/`                           |
| `Output0` | `credentials.secret`                | Name of the secret (not the ARN) in SecretsManager containing the SASL/SCRAM credentials |
| `Output0` | `credentials.secret.username.field` | Name of the field (the key) of the secret, containing the SASL/SCRAM username            |
| `Output0` | `credentials.secret.password.field` | Name of the field (the key) of the secret, containing the SASL/SCRAM password            |