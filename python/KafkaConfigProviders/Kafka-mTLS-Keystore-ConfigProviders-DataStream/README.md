## Using MSK Config Providers to set up Kafka mTLS authentication, in PyFlink DataStream API

* Flink version: 1.20
* Flink API: DataStream API
* Flink Connectors: Kafka 
* Language: Python

This example demonstrate how to configure Kafka connector for mTLS authentication in Python DataStream, 
fetching keystore and truststore at runtime, using MSK Configuration Providers.

The application reads a payload, as a string, from source Kafka topic, and publish it, unmodified, to a destination topic.

More details on the MSK Config Providers in [this repo](https://github.com/aws-samples/msk-config-providers).

* Keystore is fetched from S3, when the job starts.
* The password to open keystore is also fetched when the job starts, from AWS Secret Manager.
* You can optionally also fetch the truststore. This is not required with MSK
* No secret is packaged with the application.

### Prerequisites

This example expects:
* An MSK cluster configured for mTLS authentication.
* 2 topics
* A keystore JKS file containing a client certificate
* Kafka ACL allowing the principal of the client certificate at least read permissions in the source topic and write permissions in the destination topic

### High level approach

NOTE: This repo already includes a previously built version of the msk-config-providers JAR from [this repo](https://github.com/aws-samples/msk-config-providers).

1. Generate the keystore/trustore JKS files and upload them to an S3 bucket. Create a secret in SecretsManager with the keystore password. 
2. Build the artifact (zip) of this project using `mvn clean package`.
3. Create a Managed Flink app using the zip from the build above. Set the runtime configuration properties to point the MSK cluster, the JKS files in S3 and the secret in SecretsManager. 
4. Enable VPC integration, ensuring the application has network access to the MSK cluster. For simplicity, the applicatio can use the same Subnets and Security Group as the MSK cluster.
5. Ensure the Managed Flink application has IAM permissions to access the JKS files in S3, and the secrets in SecretsManager.
6. Ensure the Principal of the client certificate has Read and Write permissions to the topics.

### Configuring the Kafka connectors

The example show the configuration of a KafkaSource and KafkaSink using DataStream API in Python.

The setup is slightly different for source and sink, due some Flink API differences.
In both cases, use MSK Config Providers to pass `ssl.keystore.location`, that downloads the keystore JKS file from S3. 
Similarly, `ssl.keystore.password` and `ssl.key.password` use Config Providers to fetch the password from SecretsManager.

> No custom truststore is required for mTLS in MSK. MSK signs brokers' certificates with a root-CA that is recognized
> by the JVM out of the box.
> For self-managed Kafka with broker certificates that are self-signed or signed with a private CA, you also
> have to pass a truststore containing the signing root-CA. The example contains commented out the code to do that.


### IAM permissions

The application must have the following IAM permissions:
* `s3:GetObject` and `s3:GetObjectVersion` to the JKS files stored in S3
* `secretsmanager:GetSecretValue` to the secret containing the keystore password

Example IAM Policy to be added to the Managed Flink application, in addition to the permissions normally needed by the application.

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

### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](./flink-application-properties-dev.json) file located in the project folder.

Runtime parameters:

| GroupId        | Key                              | Description                                                      |
|----------------|----------------------------------|------------------------------------------------------------------|
| `InputKafka0`  | `bootstrap.servers`              | kafka cluster boostrap servers for the source topic              |
| `InputKafka0`  | `topic`                          | source topic name                                                |
| `InputKafka0`  | `group.id`                       | kafka consumer group id                                          |
| `OutputKafka0` | `bootstrap.servers`              | kafka cluster boostrap servers for the destination topic         |
| `OutputKafka0` | `topic`                          | destination topic name                                           |
| `KafkaConfig`  | `keystore.bucket.region`         | region of the S3 bucket(s) containing the keystore               |
| `KafkaConfig`  | `keystore.bucket.name`           | name of the S3 bucket containing the keystore                    |
| `KafkaConfig`  | `keystore.bucket.path`           | path to the keystore object, omitting any trailing `/`           |
| `KafkaConfig`  | `keystore.password.secret`       | SecretManager secret ID  containing the password of the keystore |
| `KafkaConfig`  | `keystore.password.secret.field` | SecretManager secret key containing the password of the keystore |

All parameters are case-sensitive.

> This example assumes the password of the keystore and the key stored in the keystore are identical. If this is not the
> case for you, modify the code and use two separate SecretsManager secrets.

> The example also assumes the source and destination topics are in the same cluster and use the same client certificate.

#### Additional configuring for PyFink application on Managed Flink

To tell Managed Flink what Python script to run and the fat-jar containing all dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                     |
|---------------------------------------|-----------|-----------|--------------------------------|---------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.          |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies. |

### Application packaging and dependencies

This uses Maven to both build an uber-jar with all Java dependencies (in this case, MSK Config Providers and Flink Kafka connector),
and to prepare the zip-file of the Managed Flink application.

Executing `mvn package` download any required Java dependency and prepare a zip file in `./target`.

When you modify the Python application, do not forget to run `mvn package` again to rebuild the zip with the modified code,
before uploading to S3.

#### Publishing code changes to Amazon Managed Service for Apache Flink

Follow this process to make changes to the Python code

1. Modify the code locally (test/run locally, as required)
2. Re-run `mvn clean package` - **if you skip this step, the zipfile is not updated**, and contains the old Python script.
3. Upload the new zip file to the same location on S3 (overwriting the previous zip file)
4. In the Managed Flink application console, enter *Configure*, scroll down and press *Save Changes*
   * If your application was running when you published the change, Managed Flink stops the application and restarts it with the new code
   * If the application was not running (in Ready state) you need to click *Run* to restart it with the new code

> 🚨 by design, Managed Flink does not detect the new zip file automatically.
> You control when you want to restart the application with the code changes. This is done saving a new configuration from the 
> console or using the [*UpdateApplication*](https://docs.aws.amazon.com/managed-flink/latest/apiv2/API_UpdateApplication.html)
> API.

### Data generator

The application does not make any assumption on the content of the messages.
You can generate any text/JSON payload to the source topic, either using the provided Python [data generator](../../data-generator) script
or the Kafka command line producer.
