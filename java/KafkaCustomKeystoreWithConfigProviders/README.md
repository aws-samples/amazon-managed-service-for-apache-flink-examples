## Sample illustrating how to use MSK config providers in Flink Kafka connectors

* Flink version: 1.19
* Flink API: DataStream API
* Language: Java (11)
* Connectors: Kafka

This sample illustrates how to configure the Flink Kafka connectors (KafkaSource and KafkaSink) with keystore and/or truststore certs using the MSK config providers described [here](https://github.com/aws-samples/msk-config-providers).

The keystore and truststore are fetched from S3 at runtime.
The password of both is fetched from AWS Secret Manager, at runtime.
No secret is packaged with the application.

### High level approach

NOTE: This repo already includes a previously built version of the msk-config-providers jar

1. Build this repo using `mvn clean package`.
2. Setup Flink app using the jar from the build above. Please follow the instructions [here](https://docs.aws.amazon.com/managed-flink/latest/java/getting-started.html).
3. Please ensure that you specify appropriate values for the application properties (S3 location, secrets manager key, etc.).

Follow the [step-by-step instructions](docs/step-by-step.md) for the details about configuring and running Flink application on Amazon Managed Service for Apache Flink, with Amazon Managed Streaming for Apache Kafka (Amazon MSK) as the source, using TLS mutual authentication.
Also look at the [troubleshooting guide](docs/troubleshoot-guide.md) for solving common issues during the setup. 


### Configuring the Kafka connector

The following snippet shows how to configure the service providers and other relevant properties.

See [StreamingJob.java](src/main/java/com/amazonaws/services/msf/StreamingJob.java):

```java

KafkaSourceBuilder<String> builder = ...
        ...
// define names of config providers:
builder.setProperty("config.providers", "secretsmanager,s3import");

// provide implementation classes for each provider:
builder.setProperty("config.providers.secretsmanager.class", "com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider");
builder.setProperty("config.providers.s3import.class", "com.amazonaws.kafka.config.providers.S3ImportConfigProvider");

String region = appProperties.get("S3BucketRegion");
String keystoreS3Bucket = appProperties.get("KeystoreS3Bucket");
String keystoreS3Path = appProperties.get("KeystoreS3Path");
String truststoreS3Bucket = appProperties.get("TruststoreS3Bucket");
String truststoreS3Path = appProperties.get("TruststoreS3Path");
String keystorePassSecret = appProperties.get("KeystorePassSecret");
String keystorePassSecretField = appProperties.get("KeystorePassSecretField");

// region, etc..
builder.setProperty("config.providers.s3import.param.region", region);

// properties
builder.setProperty("ssl.truststore.location", "${s3import:" + region + ":" + truststoreS3Bucket + "/" + truststoreS3Path + "}");
builder.setProperty("ssl.keystore.type", "PKCS12");
builder.setProperty("ssl.keystore.location", "${s3import:" + region + ":" + keystoreS3Bucket + "/" + keystoreS3Path + "}");
builder.setProperty("ssl.keystore.password", "${secretsmanager:" + keystorePassSecret + ":" + keystorePassSecretField + "}");
builder.setProperty("ssl.key.password", "${secretsmanager:" + keystorePassSecret + ":" + keystorePassSecretField + "}");

```

### IAM credentials and permissions

Currently, config providers do not support credentials to be explicitly provided. 
Config provider will inherit any type of credentials of hosting application, OS or service.

Access Policy/Role associated with the application that is running a config provider should have sufficient but least privileged permissions to access the services that are configured/referenced in the configuration. E.g., Supplying secrets through AWS Secrets Manager provider will need read permissions for the client to read that particular secret from AWS Secrets Manager service.

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from `flink-application-properties-dev.json`, when running locally.

Runtime Properties are expected in the Group ID `Input0` and they are all case-sensitive.

| GroupId | Key                     | Default     | Description                                                        |
|---------|-------------------------|-------------|--------------------------------------------------------------------|
| `Input0` | `bootstrap.servers`     |             | kafka cluster boostrap servers                                     |
| `Input0` | `topic`                 | `source`    | source topic name                                                  |
| `Input0` | `group.id`              | `flink-app` | kafka consumer group id                                            |
| `Input0` | `bucket.region`         |             | region of the S3 bucket(s) containing the keystore and truststore  |
| `Input0` | `keystore.bucket`       |             | name of the S3 bucket containing the keystore                      |
| `Input0` | `keystore.path`         |             | path to the keystore object, omitting any trailing `/`             |
| `Input0` | `truststore.bucket`     |             | name of the S3 bucket containing the truststore                    |
| `Input0` | `truststore.path`       |             | path to the truststore object, omitting any trailing `/`           |
| `Input0` | `keystore.secret`       |             | SecretManager secret ID  containing the password of the keystore   |
| `Input0` | `keystore.secret.field` |             | SecretManager secret key containing the password of the keystore   |


## Running locally in IntelliJ

To run the application in IntelliJ

1. Edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*
2. Update `flink-application-properties-dev.json` with property values (`bootstrap.servers`, `topic` etc.) that fit your environment.
