## Example showing configuring Kafka connector for SASL/SCRAM using MSK Config Providers

* Flink version: 1.19
* Flink API: DataStream API
* Language: Java (11)
* Connectors: Kafka

This example illustrates how to set up a Kafka connector to MSK using SASL/SCRAM authentication, fetching the credentials
at runtime directly from SecretsManager.

For simplicity, the application generates random data internally, using the Data Generator Source, and demonstrates how to 
set up a KafkaSink with SASL/SCRAM authentication.
The configuration of a KafkaSource is identical to the sink, for what regards SASL/SCRAM authentication.


### High level approach


This application uses config providers to fetch secrets for setting up SASL/SCRAM authentication at runtime, when the application starts.
In particular, these two config providers are used:

1. S3 config provider: to fetch the TrustStore from an S3 bucket
2. SecretsManager config provider: to retrieve SASL username and password from SecretsManager

This project includes the pre-packaged JAR with the MSK Config Providers, setting up a local repository in the [POM](./pom.xml).
You can find sources of the Config Providers in
[https://github.com/aws-samples/msk-config-providers](https://github.com/aws-samples/msk-config-providers).


### Set up dependencies

To run this application you need the following dependencies:

1. Set up an MSK cluster with SASL/SCRAM authentication configured and a SecretsManager secret containing the SASL credentials of a valid user.
   (see [SASL credentials in SecretsManager](#sasl-credentials-in-secretsmanager), below).  
2. On the MSK cluster either enable topic creation (`auto.create.topics.enable=true`) or manually create the destination topic.
3. Upload a JVM TrustStore (jks file) into an S3 bucket.  This can be a copy of the JVM default TrustStore.
   (see [TrustStore for Kafka SSL in S3](#truststore-for-kafka-ssl-in-s3), below)
4. Configure the Managed Service for Apache Flink application VPC networking, ensuring the application has network access to the MSK cluster.
   (see [Configure Managed Service for Apache Flink to access resources in an Amazon VPC](#https://docs.aws.amazon.com/managed-flink/latest/java/vpc.html) documentation for details).
5. Create a VPC Endpoint for SecretsManager in the VPC of the application.
   (see the blog post [How to connect to AWS Secrets Manager service within a Virtual Private Cloud](https://aws.amazon.com/blogs/security/how-to-connect-to-aws-secrets-manager-service-within-a-virtual-private-cloud/) for details).
6. Ensure the IAM Role of the Managed Service for Apache Flink application allows access to the TrustStore in S3 and the secret in SecretsManager
   (see [Application IAM permissions](#application-iam-permissions), below).

#### SASL credentials in SecretsManager

The application assumes the SecretsManager secret with SASL credentials is the same used by MSK. 
The secret contains two keys, username and password respectively.

See [MSK documentation](https://docs.aws.amazon.com/msk/latest/developerguide/msk-password.html) for more details about setting up 
SASL/SCRAM authentication and creating a user.

At runtime, the application uses the `secretsmanager` config provider twice, to fetch username and password, and to build the value for `sasl.jaas.config`
configuration in the format expected by the Kafka client.

```
org.apache.kafka.common.security.scram.ScramLoginModule required username="<username>" password="<password>";
```

Note: the double-quotes surrounding username and password and the closing semicolon.

Note: MSK requires SASL credentials to be encrypted with a custom AWS KMS key. The application must also have permissions to 
access the key to decrypt the secret (see [Application IAM permissions](#application-iam-permissions), below).

#### TrustStore for Kafka SSL in S3

The Kafka client expects a valid TrustStore (jks file) on the file system, even if the TLS connection to the Kafka cluster
uses certificates signed by a root CA recognised by the JDK, as it happens in MSK.
If you do not specify a `ssl.truststore.location`, the Kafka client does not fall back to the default JVM TrustStore, but
pointing `ssl.truststore.location` to a **copy** of the JDK default TrustStore is sufficient.

In this example, follow these steps:

1. Copy the JDK TrustStore from a recent JDK/JRE. On Linux this is often located in the file `/usr/lib/jvm/JDKFolder/jre/lib/security/cacerts`
2. Upload the copy of the TrustStore to S3
3. Configure the application to fetch this file (see [Application configuration parameters](#application-configuration-parameters), below)

The application uses `s3import` to fetch the TrustStore file from S3 and pass it to `ssl.truststore.location`.

Because we use a copy of the default JDK TrustStore, we assume the password is also the default "changeit".

TL;DR

The Flink Kafka connector expects this file to be present on the file system (not in a Java resource), when the operator 
is initialized (not when it is instantiated). This makes things more complicated, due to the Flink operator lifecycle.

Operators are initialized on the Task Manager, while your `main()` method runs once on the Job Manager. If you copy the file
to a known location in the file system in the `main()`, the file will be on the Job Manager, not the Task Manager.

Configuration Providers can do the job, because they are executed as part of the Kafka client initialization, when the operator
is initialized on the Task Manager. 
The `s3import` config provider takes care of downloading the file to the local file system and return the path, that can 
be uses as value of the `ssl.truststore.location` parameter.

Note: The copy of the TrustStore can be from any recent JDK or JRE. No need to be the same JDK used to run Flink. 


#### Application IAM permissions

In addition to any other IAM permission required by the application, to dynamically fetch SASL credentials the application
also requires the following permissions:

1. To read the secret from Secret Manager:
   * Actions: `secretsmanager:GetSecretValue`, `secretsmanager:DescribeSecret`, `secretsmanager:DescribeSecret`, `secretsmanager:GetResourcePolicy`, `secretsmanager:ListSecretVersionIds`
   * Resource: `arn:aws:secretsmanager:<region>:<account-id>:secret:<secret-name>`
2. To decrypt the secret stored in SecretsManager - This is the KMS key used to encrypt the secret with the SASL credentials
   * Action: `kms:Decrypt`
   * Resource: `arn:aws:kms:<region>:<account-id>:key/<key-id>`
3. To download the TrustStore file from S3
   * Actions: `s3:GetObject`
   * Resource: `arn:aws:s3:::<bucket-name>/<object-key>`

For example, and IAM Policy like the following:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SecretFromSecretManager",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:ListSecrets",
        "secretsmanager:DescribeSecret",
        "secretsmanager:GetResourcePolicy",
        "secretsmanager:ListSecretVersionIds"
      ],
      "Resource": "arn:aws:secretsmanager:<region>:<account>:secret:<secret-name>"
    },
    {
      "Sid": "DecryptSecrets",
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": "arn:aws:kms:<region>:<account>:key/<key-id>"
    },
    {
      "Sid": "ReadTrustStoreFromS3",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::<bucket-name>/<path-to-truststore-file>"
    }
  ]
}
```


Note: application uses VPC networking to MSK. This requires additional permissions. 
See [VPC application permissions](https://docs.aws.amazon.com/managed-flink/latest/java/vpc-permissions.html)
in the service documentation, for details.

Note: no IAM permissions to MSK is required for using SASL/SCRAM. When using SASL/SCRAM authentication, access to MSK
is controlled via networking (SecurityGroups, NACL) and by the SASL credentials provided.


### Application configuration parameters

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID  | Key                                 | Description                                                                                            | 
|-----------|-------------------------------------|--------------------------------------------------------------------------------------------------------|
| `Output0` | `bootstrap.servers`                 | Kafka/MSK bootstrap servers for SASL/SCRAM authentication (typically with port `9096` on MSK)          |`
| `Output0` | `topic`                             | Name of the output topic                                                                               |
| `Output0` | `bucket.region`                     | Region of the S3 bucket containing the TrustStore                                                      |
| `Output0` | `truststore.bucket`                 | Name of the S3 bucket containing the TrustStore (without any `s3://` prefix)                           |
| `Output0` | `truststore.path`                   | Path to the TrustStore, in the S3 bucket (without trailing `/` )                                       |
| `Output0` | `credentials.secret`                | Name of the secret (not the ARN) in SecretsManager containing the SASL/SCRAM credentials               |
| `Output0` | `credentials.secret.username.field` | Name of the field (the key) of the secret, containing the SASL username. Optional, default: `username` |
| `Output0` | `credentials.secret.password.field` | Name of the field (the key) of the secret, containing the SASL password. Optional, default: `password` |

All parameters are case-sensitive.

## Running locally in IntelliJ

> Due to MSK VPC networking, to run this example on your machine you need to set up network connectivity to the VPC where MSK is deployed, for example with a VPN.
> Setting this connectivity depends on your set up and is out of scope for this example.

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
