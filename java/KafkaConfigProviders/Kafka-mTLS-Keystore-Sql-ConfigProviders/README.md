## Sample illustrating how to use MSK config providers in Flink Kafka connectors, for mTLS authentication

* Flink version: 1.20
* Flink API: Table API & SQL
* Language: Java (11)
* Flink connectors: Kafka (mTLS authentication)

This sample illustrates how to configure the Flink Kafka connectors (source and sink) 
retrieving custom KeyStore at runtime, using Config Providers.
More details on the MSK Config Providers in [this repo](https://github.com/aws-samples/msk-config-providers).

* KeyStore is fetched from S3, when the job starts.
* The password to open KeyStore is also fetched when the job starts, from AWS Secret Manager.
* No secret is packaged with the application.

### High level approach

NOTE: This repo already includes a previously built version of the msk-config-providers JAR from [this repo](https://github.com/aws-samples/msk-config-providers).

1. Build this repo using `mvn clean package`.
2. Setup Flink app using the jar from the build above. Please follow the instructions [here](https://docs.aws.amazon.com/managed-flink/latest/java/getting-started.html).
3. Please ensure that you specify appropriate values for the application properties (S3 location, secrets manager key, etc.).

Also look at the [troubleshooting guide](docs/troubleshoot-guide.md) for solving common issues during the setup. 


### Configuring the Kafka connector

The following snippet shows how to configure the service providers and other relevant properties.

See [StreamingJob.java](src/main/java/com/amazonaws/services/msf/StreamingJob.java):

```java

tableEnv.executeSql(
            "CREATE TABLE sourceTable (" +
                    "  `user` STRING, " +
                    "  `message` STRING, " +
                    "  `ts` TIMESTAMP(3) " +
                    ") WITH (" +
                    "  'connector' = 'kafka'," +
                    "  'topic' = '" + inputProperties.getProperty(KAFKA_SOURCE_TOPIC_KEY, DEFAULT_SOURCE_TOPIC) + "'," +
        "  'properties.bootstrap.servers' = '" + inputProperties.getProperty(SOURCE_MSKBOOTSTRAP_SERVERS_KEY) + "'," +
        "  'properties.group.id' = '" + inputProperties.getProperty(KAFKA_CONSUMER_GROUP_ID_KEY, DEFAULT_CONSUMER_GROUP) + "'," +
        "  'properties.security.protocol' = 'SSL'," +
        "  'properties.config.providers' = 'secretsmanager,s3import'," +
        "  'properties.config.providers.secretsmanager.class' = 'com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider'," +
        "  'properties.config.providers.s3import.class' = 'com.amazonaws.kafka.config.providers.S3ImportConfigProvider'," +
        "  'properties.config.providers.s3import.param.region' = '" + inputProperties.getProperty(S3_BUCKET_REGION_KEY) + "'," +
        "  'properties.ssl.keystore.type' = 'PKCS12'," +
        "  'properties.ssl.keystore.location' = '${s3import:" + inputProperties.getProperty(S3_BUCKET_REGION_KEY) + ":" + inputProperties.getProperty(KEYSTORE_S3_BUCKET_KEY) + "/" + inputProperties.getProperty(KEYSTORE_S3_PATH_KEY) + "}'," +
        "  'properties.ssl.keystore.password' = '${secretsmanager:" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
        "  'properties.ssl.key.password' = '${secretsmanager:" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_KEY) + ":" + inputProperties.getProperty(KEYSTORE_PASS_SECRET_FIELD_KEY) + "}'," +
        "  'scan.startup.mode' = 'earliest-offset'," +
        "  'format' = 'json'," +
        "  'json.ignore-parse-errors' = 'true'" +
        ")"
        );

```

### IAM credentials and permissions

Currently, config providers do not support credentials to be explicitly provided. 
Config provider will inherit any type of credentials of hosting application, OS or service.

Access Policy/Role associated with the application that is running a config provider should have sufficient but least privileged permissions to access the services that are configured/referenced in the configuration. E.g., Supplying secrets through AWS Secrets Manager provider will need read permissions for the client to read that particular secret from AWS Secrets Manager service.

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| GroupId | Key                     | Default     | Description                                                        |
|---------|-------------------------|-------------|--------------------------------------------------------------------|
| `Input0` | `bootstrap.servers`     |             | kafka cluster boostrap servers                                     |
| `Input0` | `topic`                 | `source`    | source topic name                                                  |
| `Input0` | `group.id`              | `flink-app` | kafka consumer group id                                            |
| `Input0` | `bucket.region`         |             | region of the S3 bucket(s) containing the keystore  |
| `Input0` | `keystore.bucket`       |             | name of the S3 bucket containing the keystore                      |
| `Input0` | `keystore.path`         |             | path to the keystore object, omitting any trailing `/`             |
| `Input0` | `keystore.secret`       |             | SecretManager secret ID  containing the password of the keystore   |
| `Input0` | `keystore.secret.field` |             | SecretManager secret key containing the password of the keystore   |
| `Output0` | `bootstrap.servers`     |             | kafka cluster boostrap servers                                     |
| `Output0` | `topic`                 | `sink`    | sink topic name                                                  |
| `Output0` | `group.id`              | `flink-app` | kafka consumer group id                                            |
| `Output0` | `bucket.region`         |             | region of the S3 bucket(s) containing the keystore  |
| `Output0` | `keystore.bucket`       |             | name of the S3 bucket containing the keystore                      |
| `Output0` | `keystore.path`         |             | path to the keystore object, omitting any trailing `/`             |
| `Output0` | `keystore.secret`       |             | SecretManager secret ID  containing the password of the keystore   |
| `Output0` | `keystore.secret.field` |             | SecretManager secret key containing the password of the keystore   |


All parameters are case-sensitive.

## Running locally in IntelliJ

> Due to MSK VPC networking, to run this example on your machine you need to set up network connectivity to the VPC where MSK is deployed, for example with a VPN.
> Setting this connectivity depends on your set up and is out of scope for this example.

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../../running-examples-locally.md) for details.
