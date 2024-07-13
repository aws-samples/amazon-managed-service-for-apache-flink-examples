## Sample illustrating how to use MSK config providers in Flink Kafka connectors

* Flink version: 1.19
* Flink API: DataStream API
* Language: Java (11)

This sample illustrates how to configure the Flink Kafka connectors (KafkaSource and KafkaSink) with keystore and/or truststore certs using the MSK config providers described [here](https://github.com/aws-samples/msk-config-providers).

The keystore and truststore are fetched from S3 at runtime.
The password of both is fetched from AWS Secret Manager, at runtime.
No secret is packaged with the application.

### High level approach

NOTE: Steps 1 and 2 are optional because this repo already includes a previously built version of the msk-config-providers jar

1. (Optional) Clone and build [MSK config providers repo](https://github.com/aws-samples/msk-config-providers).
2. (Optional) Pull in the built jar into `local-repo` in this repo (See [pom.xml](pom.xml)).
3. Build this repo using `mvn clean package`.
4. Setup Flink app using the jar from the build above. Please follow the instructions [here](https://docs.aws.amazon.com/managed-flink/latest/java/getting-started.html).
5. Please ensure that you specify appropriate values for the application properties (S3 location, secrets manager key, etc...).

See [here](docs/FLINKAPP_MSF_MTLS_SAMPLE.md) for a detailed example describing the steps performed to configure and run Apache Flink application on Amazon Managed Service for Apache Flink (Amazon MSF), with Amazon Managed Streaming for Apache Kafka (Amazon MSK) as the source, using TLS mutual authentication.

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
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--` and separated by space.

They are all case-sensitive.

Configuration parameters:

* `MSKBootstrapServers` cluster boostrap servers
* `KafkaSourceTopic` source topic (default: `source`)
* `KafkaConsumerGroupId` consumer group id (default: `flink-app`)
* `S3BucketRegion` region of the S3 bucket containing the keystore and truststore
* `KeystoreS3Bucket` name of the S3 bucket containing the keystore
* `KeystoreS3Path` path to the keystore object, omitting any trailing `/` 
* `TruststoreS3Bucket` name of the S3 bucket containing the truststore
* `TruststoreS3Path` path to the truststore object, omitting any trailing `/` 
* `KeystorePassSecret` and `KeystorePassSecretField` SecretManager secret ID and key containing the password of the keystore

## Running locally in IntelliJ

To run the application in IntelliJ

1. Edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*
2. Pass all configuration parameters from the command line, prepending `--` to each parameter (e.g. `--MSKBootstrapServers localhost:9094 --KafkaSourceTopic source-topic ...`)
