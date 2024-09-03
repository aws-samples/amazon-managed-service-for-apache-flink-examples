## Configuring Kafka connectors secrets at runtime, using Config Providers

This directory includes example that shows how to configure secrets for Kafka connector authentication
scheme at runtime, using [MSK Config Providers](https://github.com/aws-samples/msk-config-providers).

Using Config Providers, secrets and files (TrustStore and KeyStore) required to set up the Kafka authentication
and SSL, can be fetched at runtime and not embedded in the application JAR.

* [Configuring mTLS TrustStore and KeyStore using Config Providers](./Kafka-mTLS-KeystoreWithConfigProviders)
* [Configuring SASL/SCRAM (SASL_SSL) TrustStore and credentials using Config Providers](./Kafka-SASL_SSL-WithConfigProviders)
