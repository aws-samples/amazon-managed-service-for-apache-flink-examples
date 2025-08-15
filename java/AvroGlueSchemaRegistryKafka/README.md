## AVRO serialization in KafkaSource and KafkaSink using AWS Glue Schema Registry

This example demonstrates how to serialize/deserialize AVRO messages in Kafka sources and sinks, using
[AWS Glue Schema Registry](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html) (GSR).

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Connectors: Kafka connector, DataGenerator

The example contains two Flink applications: 
1. a [producer](./producer) job, which generates random temperature samples and publishes to Kafka as AVRO using GSR.
2. a [consumer](./consumer) job, which reads temperature records from the same topic, using GSR.

Both applications use AVRO-specific records based on the schema definitions provided at compile time.
The record classes are generated during the build process based on the AVRO IDL (`.avdl`) you can find in the resources
folder of both jobs. For simplicity, the schema definition is repeated in both jobs.

The two jobs are designed to run as separate Amazon Managed Service for Apache Flink applications, connecting to the
same Amazon Managed Streaming for Kafka (MSK) cluster.

The default configuration uses unauthenticated connection to MSK. The example can be extended to implement any supported 
MSK authentication scheme.

### Prerequisites

To run the two Managed Flink applications you need to set up the following prerequisites:

1. An MSK cluster 
    - Create the topic `temperature-samples` or enable auto topic creation
    - Allow unauthenticated access (or modify the application to support the configured authentication scheme)
2. Create a Registry named `temperature-schema-registry` in Glue Schema Registry, in the same region
3. ⚠️ Create a VPC Endpoint for Glue in the VPC where the Managed Flink applications are attached. 
   Without VPCE an application connected to a VPC may not be able to connector to a service endpoint.

### Create the Amazon Managed Service for Apache Flink applications

Create two Managed Flink applications, one for the producer and the other for the consumer.
1. Build both jobs by running `mvn package` in the directory of the example. This will build two JARs in the `target` subfolder of the producer and consumer.
2. Upload both JARs to an S3 bucket and use them as application code, for producer and consumer respectively..
3. Application configuration
    * Attach both applications to a VPC with access to the MSK cluster and ensure the Security Group allows access to the MSK cluster.
      For simplicity, to run the example we suggest to use for both applications the same VPC, same subnets, and same Security Group as the MSK cluster
    * Ensure the applications have permissions to access Glue Schema Registry. For the sake of this example you can attach
      the policy `AWSGlueSchemaRegistryFullAccess` to the producer application's IAM Role, and the policy `AWSGlueSchemaRegistryReadonlyAccess`
      to the consumer's Role.
    * Set up the Runtime properties of the two applications as described in the following sections.

### Runtime configuration

The two applications expect different configurations.
When running locally the configurations are fetched from the `flink-application-properties-dev.json` files in the resources
folder of each job.

When running on Managed Flink these files are ignored and the configuration must be passed using the Runtime properties
as part of the configuration of each application.

All parameters are case-sensitive.

#### Producer runtime parameters

| Group ID          | Key                   | Description                                                    |
|-------------------|-----------------------|----------------------------------------------------------------|
| `Output0`         | `bootstrap.servers`   | Kafka bootstrap servers                                        |
| `Output0`         | `topic`               | Kafka topic name for temperature samples                       |
| `SchemaRegistry`  | `name`                | AWS Glue Schema Registry name                                  |
| `SchemaRegistry`  | `region`              | AWS region where the Schema Registry is located                |
| `DataGen`         | `samples.per.second`  | (optional) Rate of sample generation per second (default: 100) |



#### Consumer runtime parameters

| Group ID          | Key                   | Description                                     |
|-------------------|-----------------------|-------------------------------------------------|
| `Input0`          | `bootstrap.servers`   | Kafka bootstrap servers                         |
| `Input0`          | `topic`               | Kafka topic name for temperature samples        |
| `Input0`          | `group.id`            | Kafka consumer group ID                         |
| `SchemaRegistry`  | `name`                | AWS Glue Schema Registry name                   |
| `SchemaRegistry`  | `region`              | AWS region where the Schema Registry is located |


### Running the applications locally

A [docker-compose](docker/docker-compose.yml) file is provided to run a local Kafka cluster for local development. 
The default configurations use this local cluster.

When running locally the jobs will use the actual Glue Schema Registry.
Make sure the machine where you are developing has an authenticated AWS CLI profile with permissions to use GSR. Use the
AWS Plugin of your IDE to make the application run with a specific AWS profile.

See [Running examples locally](../running-examples-locally.md) for further details.


### Notes about using AVRO with Apache Flink

#### AVRO-generated classes

This project uses classes generated at build-time as data objects.

As a best practice, only the AVRO schema definitions (IDL `.avdl` files in this case) are included in the project source 
code. The AVRO Maven plugin generates the Java classes (source code) at build-time, during the 
[`generate-source`](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html) phase.

The generated classes are written into `./target/generated-sources/avro` directory and should **not** be committed with 
the project source. This way, the only dependency is on the schema definition file(s).
If any change is required, the schema file is modified and the AVRO classes are re-generated automatically in the build.

Code generation is supported by all common IDEs like IntelliJ. 
If your IDE does not see the AVRO classes (`TemperatureSample`) when you import the project for the 
first time, you may manually run `mvn generate-sources` once or force source code generation from the IDE.

#### AVRO-generated classes (SpecificRecord) in Apache Flink

Using AVRO-generated classes (SpecificRecord) within the flow of the Flink application (between operators) or in the 
Flink state, has an additional benefit. 
Flink will [natively and efficiently serialize and deserialize](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#pojos) 
these objects, without the risk of falling back to Kryo.

### Common issues

If the application fails to call the Glue Schema Registry API for any reasons, the job gets trapped in a fail-and-restart 
loop. The exception says it cannot fetch or register a schema version.

The inability to use GSR may be caused by:
* Lack of permissions to access GSR --> add `AWSGlueSchemaRegistryFullAccess` or `AWSGlueSchemaRegistryReadonlyAccess` policies to the application IAM Role
* Unable to reach the Glue endpoint --> create a Glue VPC Endpoint in the application VPC
* The Registry does not exist --> create a registry with the configured name (`temperature-schema-registry` by default)
* Misconfiguration --> ensure the registry name and region passed to the application match your setup