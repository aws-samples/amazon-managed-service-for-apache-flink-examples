## Flink Kafka Source & Sink Examples

* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Java (11)


This example demonstrate how to use
[Flink Kafka Connector](https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/kafka/),
source and sink.

This example uses on `KafkaSource` and `KafkaSink`.

Note that the old 
[`FlinkKafkaConsumer`](https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/connectors/datastream/kafka/#kafka-sourcefunction) 
and [`FlinkKafkaProducers`](https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/connectors/datastream/kafka/#kafka-producer)
are deprecated since Flink 1.15

### Runtime configuration

The application reads the runtime configuration from the Runtime Properties, when running on Amazon Managed Service for Apache Flink,
or from command line parameters, when running locally.

Runtime Properties are expected in the Group ID `FlinkApplicationProperties`.
Command line parameters should be prepended by `--`.

They are all case-sensitive.

Configuration parameters:

* `source.bootstrap.servers` source cluster boostrap servers
* `source.topic` source topic (default: `source`)
* `sink.bootstrap.servers` sink cluster bootstrap servers
* `sink.topic` sink topic (default: `destination`)
* `sink.transaction.timeout.ms` Sink transaction timeout 

If you are connecting with no-auth and no SSL, above will work. Else you need additional configuration for both source and sink. 

#### For SSL
                source.security.protocol: SSL
                source.ssl.truststore.location: /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts
                source.ssl.truststore.password: changeit
                sink.security.protocol: SSL
                sink.ssl.truststore.location: /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts
                sink.ssl.truststore.password: changeit
#### For IAM Auth
                source.sasl.mechanism: AWS_MSK_IAM
                source.sasl.client.callback.handler.class: software.amazon.msk.auth.iam.IAMClientCallbackHandler
                source.sasl.jaas.config: "software.amazon.msk.auth.iam.IAMLoginModule required;"
                source.security.protocol: SASL_SSL
                source.ssl.truststore.location: /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts
                source.ssl.truststore.password: changeit
                sink.sasl.mechanism: AWS_MSK_IAM
                sink.sasl.client.callback.handler.class: software.amazon.msk.auth.iam.IAMClientCallbackHandler
                sink.sasl.jaas.config: "software.amazon.msk.auth.iam.IAMLoginModule required;"
                sink.security.protocol: SASL_SSL
                sink.ssl.truststore.location: /usr/lib/jvm/java-11-amazon-corretto/lib/security/cacerts
                sink.ssl.truststore.password: changeit

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.

Provide arguments like following -
```
--source.bootstrap.servers localhost:9092 --source.topic source --sink.bootstrap.servers localhost:9092 --sink.topic sink --sink.transaction.timeout.ms 1000
```

### Running locally through MVN command line
Refer following sample -
```
 mvn exec:java -Dexec.classpathScope="compile" -Dexec.mainClass="com.amazonaws.services.msf.KafkaStreamingJob" -Dexec.args="--source.bootstrap.servers localhost:9092 --source.topic source --sink.bootstrap.servers localhost:9092 --sink.topic sink --sink.transaction.timeout.ms 1000" 

```

### Deploying using CloudFormation to Amazon Managed Service for Apache Flink

#### Connecting to MSK Serverless using IAM Auth
Refer the sample CloudFormation template at `cloudformation/template-iam-auth.yaml` . 
The sample assumes that both source and sink clusters need IAM auth. 
Edit `deploy-iam-auth.sh` and execute following command. The command optionally builds jar and uploads to provided bucket. 
The CloudFormation needs the jar to be there at s3://<bucket-name>/flink/kafka-connectors-1.0.jar. 

```
./deploy-iam-auth.sh <bucket-name-to-upload> <Y/N If build code and upload or not>

-- For the first deployment when you want jar to be built run 
./deploy-iam-auth.sh <bucket-name-to-upload> 

-- For subsequent deployments when there is only change in CloudFormation or parameters run 
./deploy-iam-auth.sh <bucket-name-to-upload>  N
```

#### Connecting to MSK Provisioned over SSL, no Auth
Refer the sample CloudFormation template at `cloudformation/template-no-auth-ssl.yaml` .
The sample assumes that both source and sink clusters  need no auth , but connects over SSL.
Edit `deploy-no-auth-ssl.sh` and execute following command. The command optionally builds jar and uploads to provided bucket.
The CloudFormation needs the jar to be there at s3://<bucket-name>/flink/kafka-connectors-1.0.jar.

```
./deploy-no-auth-ssl.sh <bucket-name-to-upload> <Y/N If build code and upload or not>

-- For the first deployment when you want jar to be built run 
./deploy-no-auth-ssl.sh <bucket-name-to-upload> 

-- For subsequent deployments when there is only change in CloudFormation or parameters run 
./deploy-no-auth-ssl.sh <bucket-name-to-upload>  N
```

#### Connecting to MSK Provisioned over plaintext , no Auth
Refer the sample CloudFormation template at `cloudformation/template-no-auth-plaintext.yaml` .
The sample assumes that both source and sink clusters  need no auth , and connect over plaintext.
Edit `deploy-no-auth-plaintext.sh` and execute following command. The command optionally builds jar and uploads to provided bucket.
The CloudFormation needs the jar to be there at s3://<bucket-name>/flink/kafka-connectors-1.0.jar.

```
./deploy-no-auth-plaintext.sh <bucket-name-to-upload> <Y/N If build code and upload or not>

-- For the first deployment when you want jar to be built run 
./deploy-no-auth-plaintext.sh <bucket-name-to-upload> 

-- For subsequent deployments when there is only change in CloudFormation or parameters run 
./deploy-no-auth-plaintext.sh <bucket-name-to-upload>  N
```