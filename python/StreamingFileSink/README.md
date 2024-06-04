## Example of writing to S3 in PyFlink 

* Flink version: 1.19.0
* Flink API: SQL/Table API
* Language: Python

Sample application writing to S3 as json.

### Runtime configuration

* Local development: reads [application_properties.json](./application_properties.json)
* Deployed on Amazon Managed Service for Apache Fink: set up Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)