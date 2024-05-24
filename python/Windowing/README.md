## Examples of windowing aggregations, with PyFlink/SQL

Example showing a basic PyFlink job doing data aggregation over time windows.

* Flink version: 1.18
* Flink API: Table API & SQL
* Flink Connectors: Kinesis Connector
* Language: Python (3.10)

The application demonstrate the behaviour some of the most used combinations of windowing: 
sliding and tumbling windows, in processing and event time.

The application is written in Python, but operators are defined using SQL.
This is a popular way of defining applications in PyFlink, but not the only one. You could attain the same results
using Table API ar DataStream API, in Python.

The job can run both on Amazon Managed Service for Apache Flink, and locally for development.

### Requirements

#### Development and build environment requirements

* Pyhon 3.10
* PyFlink library: `apache-flink==1.18.1`
* Java JDK 11+ and Maven

> JDK and Maven are required to download and package any required Flink dependencies, e.g. connectors, and
  to package the application as `.zip` file, for deployment to Amazon Managed Service for Apache Flink.

#### Runtime dependencies

The application expects 4 Kinesis Data Streams. Default names are:
* `SlidingWindowProcessingTimeOutput`
* `SlidingWindowEventTimeOutput`
* `TumblingWindowProcessingTimeOutput`
* `TumblingWindowEventTimeOutput`

Single-shard Streams in Provisioned mode will be sufficient for the emitted throughput.

#### IAM permissions

The application must have sufficient permissions to publish data to the Kinesis Data Streams.

When running locally, you need active valid AWS credentials that allow publishing data to the Streams.

### Runtime configuration

* Local development: uses the local file [application_properties.json](./application_properties.json)
* On Amazon Managed Service for Apache Fink: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)


### How to run and build the application

#### Local development - in the IDE

1. Make sure you have created the Kinesis Streams and you have a valid AWS session that allows you to publish to the Streams (the way of doing it depends on your setup)
2. Run `mvn package` once, from this directory. This step is required to download the jar dependencies - the Kinesis connector in this case
3. Set the environment variable `IS_LOCAL=true`. You can do from the prompt or in the run profile of the IDE
4. Run `main.py`

> Note that the application does not log nor print anything while working correctly.
> To see the output, use the Data Viewer from the Kinesis console

> If you modify the Python code, you don't need to re-run `mvn package`. Just stop and restart the Python script.

#### Deploy and run on Amazon Managed Service for Apache Flink

1. Make sure you have the 4 required Kinesis Streams
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to all the 4 Kinesis Streams
4. Package the application: run `mvn package` from this directory
5. Upload to an S3 bucket the zip file named `managed-flink-pyflink-windows-examples-1.0.0.zip` you find in the [`./target`](./target) subdirectory that was created when you run `mvn package`
6. Configure the Managed Flink application: set Application Code Location to the bucket and zip file you just uploaded
7. Configure the Runtime Properties of the application, creating the Group ID, Keys and Values as defined in the [application_properties.json](./application_properties.json)
8. Start the application
9. When the application transitions to "Ready" you can open the Flink Dashboard to verify the job is running, and you can inspect the data published to the Kinesis Streams, using the Data Viewer in the Kinesis console.

#### Publishing code changes to Amazon Managed Service for Apache Flink

1. Modify the code locally
2. Re-run `mvn package` - **if you skip this step, the zipfile is not updated with your code changes**
3. Upload the new zip file to the same location on S3 (overwriting the previous zip file)
4. In the Managed Flink application console, enter *Configure*, scroll down and press *Save Changes*
   * If you application was running when you published the change, Managed Flink stops the application and restarts it with the new code
   * If the application was not running (in Ready state) you need to click *Run* to restart it with the new code

> If you skip entering *Configure* and pressing *Save Changes*, Managed Flink does not detect the code change, and continues running with the old code.


### Application structure

The application generates synthetic data using the [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/connectors/table/datagen/) connector.
No external data generator is required.

It demonstrates 4 types of windowing aggregations:

* Sliding Window based on processing time
* Sliding Window based on event time
* Tumbling Window based on processing time
* Tumbling Window based on event time

The result of each aggregation is sent to a different Kinesis Data Streams. 
You can inspect the results consuming the Streams or using the Data Viewer from the AWS Console.

### Application packaging and dependencies

This examples also demonstrate how to include jar dependencies - e.g. connectors - in a PyFlink application, and how to 
package it, for deploying on Amazon Managed Service for Apache Flink.

Any jar dependencies must be added to the `<dependencies>` block in the [pom.xml](pom.xml) file.
In this case, you can see we have included `flink-sql-connector-kinesis`

Executing `mvn package` takes care of downloading any defined dependencies and create a single "fat-jar" containing all of them.
This file, is generated in the `./target` subdirectory and is called `pyflink-dependencies.jar`

> Note that this file must not be committed into git.

When running locally, for example in your IDE, PyFlink will look for this jar file in `./target`.

When you are happy with your Python code and you are ready to deploy the application to Amazon Managed Service for Apache Flink,
run `mvn package` **again**. The zip file you find in `./target` is the artifact to upload to S3, containing
both jar dependencies and your Python code.

Managed Flink will pick up the correct Python script and fat-jar from the zip, based on the following Application properties
you have to set

| Group ID | Key | Value | Notes                                                                   |
|----------|-----|-------|-------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python` | `main.py` | Specify the Python script containing the main() method to start the job |
| `kinesis.analytics.flink.run.options` | `jarfile` | `lib/pyflink-dependencies.jar` | Location, within the zip, of the fat-jar containing all jar dependencies |

> Note that you need to specify these properties exactly as defined here
