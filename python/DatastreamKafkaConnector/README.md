## Example of PyFlink application using Kafka connector


* Flink version: 1.15.2
* Flink API: DataStream API
* Language: Python

Sample application reading from and writing JSON data to Kafka.

This example demonstrates 
* How to consume & produce records using pyFlink datastream connector for Kafka. 
* Serialize/Deserialize Json data
* Use of value state


### Additional dependencies

Required libraries to add
* flink-connector-kafka-1.15.2.jar ,
* kafka-clients-3.3.1.jar

In order to run from local, download the above to jar,  add it other lib folder and then refer the path in code.

However, to run on Amazon managed Flink, we need to **create a UBER jar** (fat-jar) with these two jars and refer to that at `jarfile` key of property group `kinesis.analytics.flink.run.options`

To package multple jar

### Runtime configuration

* Local development: reads [application_properties.json](./application_properties.json)
* Deployed on Amazon Managed Service for Apache Fink: set up Runtime Properties, using Groupd ID and property names based on the content of [application_properties.json](./application_properties.json)
