## Example of Tumbling Windows in PyFlink/SQL

* Flink version: 1.15.2
* Flink API: SQL/Table API
* Language: Python

Sample PyFlink application reading from and writing to Kinesis Data Stream, applying tumbling windows.

### Runtime configuration

* Local development: reads [application_properties.json](./application_properties.json)
* Deployed on Amazon Managed Service for Apache Fink: set up Runtime Properties, using Groupd ID and property names based on the content of [application_properties.json](./application_properties.json)

### Sample data

Use the [Python script](../data-generator/) to generate sample stock data to Kinesis Data Stream.