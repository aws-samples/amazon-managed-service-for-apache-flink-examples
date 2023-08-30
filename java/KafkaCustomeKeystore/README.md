## Custom keystore for Kafka connector

* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Java (11)

This sample shows how to include in the Flink application UBER-jar the certificate files associated with [custom keystore and truststore for Kafka](https://kafka.apache.org/documentation/streams/developer-guide/security.html#id2).
When using the Flink connector for Kafka, you have to specify the truststore location. 
In order to ensure that the certificate files are available on each of the nodes on which the connector runs, we have to perform a custom initialization.

For further details on how to configure the cluster and the Kinesis Data Analytics application, refer to the instructions in [custom truststore with Amazon MSK](https://docs.aws.amazon.com/managed-flink/latest/java/example-keystore.html).

### Steps for building/packaging

1. Include your cert(s) under the `src/main/resources/` folder.
2. The code that grabs the cert(s) from the resources folder is in [CustomFlinkKafkaUtil.java](src/main/java/com/amazonaws/services/msf/CustomFlinkKafkaUtil.java). You can change the name(s) of the cert(s) to reflect what's in this
   class or change the code to reflect the name(s) of your cert(s).
3. Build the project: `mvn package`

After running the above command, you should see the built UBER-jar file under the `target/` folder: `kafka-custom-keystore-1.0.jar`


### Implementation details

For those interested, here's a deeper look at the implementation details:

1. Package the certificate files as a resource in containing jar. NOTE: Please include your own certs in the `src/main/resources` folder.
2. Extend the `SimpleStringSerializationSchema` and `SimpleStringDeserializationSchema` classes so that we can perform initialization of the truststore on the open method.
3. Drop our certs in `/tmp` during connector initialization so it'll be available when the connector runs.
4. Please pay close attention to the included [POM file](pom.xml) for details on which dependent libraries are necessary and how to configure them.

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

### Running locally in IntelliJ

To start the Flink job in IntelliJ edit the Run/Debug configuration enabling *'Add dependencies with "provided" scope to the classpath'*.
