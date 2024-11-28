# Flink File Cache usage example

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: DataGeneratorSource, DiscardingSink


This example demonstrate how to use Flink's distributed cache to copy files over from JobManager and distribute them to the TaskManager worker nodes.

In this example, we download a `cacerts` file for custom trust store from S3, and configure the TaskManager JVM to use this new trust store location.

We could also download directly from S3 to the taskmanager locations. However, using the filecache is a more Flink-native way to do this, and will ensure that the file is available even on job restarts.

### Running in IntelliJ

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.
