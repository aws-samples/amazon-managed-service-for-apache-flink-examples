## Fetching Secrets from Secrets Manager

This example demonstrates how to fetch secrets from AWS Secrets Manager at application start.

* Flink version: 1.20
* Flink API: DataStream
* Language: Java (11)
* Flink connectors: DataGen, Kafka sink

This example shows how you can fetch any secrets from AWS Secrets Manager, without passing them as non-encrypted configuration parameters.
In this case, the job is fetching username and password for MSK SASL/SCRAM authentication.
The application generates random stock prices and writes them, as JSON, to a Kafka topic.

Note that this method works for any secrets represented as text, which are directly passed to the constructor of the operator.
This method does not work for fetching keystore or truststore files.

### Prerequisites

#### MSK

To run this application on Amazon Managed Service for Apache Flink, you need an Amazon MSK cluster configured for 
SASL/SCRAM authentication. See [MSK Documentation](https://docs.aws.amazon.com/msk/latest/developerguide/msk-password-tutorial.html)
for details on how to set it up.

The cluster must contain a topic named `stock-prices` or allow auto topic creation.

If you set up any Kafka ACL, the user must have permissions to write to this topic.

#### Managed Flink Application Service Role

The IAM Service Role attached to the Managed Flink application must have sufficient permissions to fetch the credentials 
from Amazon Secrets Manager. See [Amazon Secrets Manager documentation](https://docs.aws.amazon.com/secretsmanager/latest/userguide/determine-acccess_examine-iam-policies.html)
for further details.

MSK SASL/SCRAM credentials must be encrypted with a customer managed key (CMK). The application Service Role must also
provide permissions to use the CMK to decrypt the secret (`kms:Decrypt`).

Here is an example of an IAM Policy to allow the application to fetch and decrypt the secret:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowFetchSecret", 
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:<region>:<account>:secret:<secretName>-*"
    },
    {
      "Sid": "AllowDecryptSecret",
      "Effect": "Allow",
      "Action": "kms:Decrypt",
      "Resource": "arn:aws:kms:<region>:<account>:key/<key-id>"
    }
  ]
}
```

⚠️ Note that the KMS Key Policy may also restrict access to the CMK.
If you are using a restrictive Key Policy, you also need to allow your Managed Flink application to decrypt.
Add the following snippet to the KMS Key Policy, in addition to other permissions:

```json
{
  "Sid": "AllowDecrypting",
  "Effect": "Allow",
  "Principal": {
    "Service": "kinesisanalytics.amazonaws.com"
  },
  "Action": "kms:Decrypt",
  "Resource": "*"
}
```

#### Managed Flink Application VPC Networking

To be able to connect to the MSK cluster, the Managed Flink application must have VPC networking configured, and must 
be able to reach the MSK cluster. For the sake of this example, the simplest setup is using the same VPC, Subnets, and Security Group
as the MSK cluster.

### Runtime Configuration

When running on Amazon Managed Service for Apache Flink, the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](src/main/resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID         | Key                  | Description                                                              | 
|------------------|----------------------|--------------------------------------------------------------------------| 
| `DataGen`        | `records.per.second` | Number of stock price records to generate per second (default: 10)       |
| `Output0`        | `bootstrap.servers`  | Kafka bootstrap servers                                                  |
| `Output0`        | `topic`              | Target Kafka topic (default: "stock-prices")                             |
| `AuthProperties` | `secret.name`        | AWS Secrets Manager secret name containing username/password credentials |

The `bootstrap.servers` should be the one for SASL/SCRAM (port 9096).

### Testing Locally

The application cannot be run locally, unless you provide networking from your machine to an MSK cluster supporting 
SASL/SCRAM authentication, for example via VPN.

Fetching the secret from Secrets Manager works from your machine, as long as you have an authenticated AWS CLI profile
which allows fetching the secret, and you let your application use the profile using the IDE AWS Plugin.


### Known Limitations

Credentials can be fetched only once, when the job starts.
Flink does not have any easy way to dynamically update an operator, for example the Kafka Sink, while the job is running.

If you implement any credential rotation, the new credentials will not be used by the application unless you restart the job.
