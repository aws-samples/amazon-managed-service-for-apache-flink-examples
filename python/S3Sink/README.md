## Example of writing to S3 in PyFlink 

* Flink version: 1.18
* Flink API: Table API & SQL
* Flink Connectors: FileSystem (S3)
* Language: Python (3.10)

Sample application writing JSON files to S3

### Runtime configuration

* Local development: reads [application_properties.json](./application_properties.json)
* Deployed on Amazon Managed Service for Apache Fink: set up Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

TODO explain to edit the bucket name to match your bucket

TODO explain that files will appear in the bucket after the checkpoint interval (1 min when running locally)