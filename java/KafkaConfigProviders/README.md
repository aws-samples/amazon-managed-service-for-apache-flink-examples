# Kafka Config Providers Examples

Examples demonstrating secure configuration management for Kafka connectors using MSK Config Providers in Amazon Managed Service for Apache Flink.

These examples show how to configure secrets and certificates for Kafka connector authentication at runtime, 
without embedding sensitive information in the application JAR, leveraging [MSK Config Providers](https://github.com/aws-samples/msk-config-providers).

## Table of Contents

### mTLS Authentication
- [**Kafka mTLS with DataStream API**](./Kafka-mTLS-Keystore-ConfigProviders) - Using Config Providers to fetch KeyStore and passwords for mTLS authentication with DataStream API
- [**Kafka mTLS with Table API & SQL**](./Kafka-mTLS-Keystore-Sql-ConfigProviders) - Using Config Providers to fetch KeyStore and passwords for mTLS authentication with Table API & SQL

### SASL Authentication
- [**Kafka SASL/SCRAM**](./Kafka-SASL_SSL-ConfigProviders) - Using Config Providers to fetch SASL/SCRAM credentials from AWS Secrets Manager

Note: SASL/SCRAM authentication with MSK can also be implemented fetching the credentials from AWS Secrets Manager on application
start, using AWS SDK, as demonstrated in the [Fetch Secrets](../FetchSecrets) example. 
MSK TLS uses a certificate signed with AWS CA which is recognized by JVM. Passing the truststore is not required.