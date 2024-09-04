## Examples of windowing aggregations, with PyFlink/SQL

Example showing a basic PyFlink job doing data aggregation over time windows.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: Kinesis Connector
* Language: Python

The application demonstrates the behavior some of the most used combinations of windowing: 
sliding and tumbling windows, in processing and event time.

The application is written in Python, but operators are defined using SQL.
This is a popular way of defining applications in PyFlink, but not the only one. You could attain the same results
using Table API ar DataStream API, in Python.

The job can run both on Amazon Managed Service for Apache Flink, and locally for development.

---

### Requirements

#### Development and build environment requirements

* Python 3.11
* PyFlink library: `apache-flink==1.20.0`
* Java JDK 11+ and Maven

> JDK and Maven are used to download and package any required Flink dependencies, e.g. connectors, and
  to package the application as `.zip` file, for deployment to Amazon Managed Service for Apache Flink.

> ⚠️ As of 2024-06-27, the Flink Python library 1.19.x may fail installing on Python 3.12.
> We recommend using Python 3.11 for development, the same Python version used by Amazon Managed Service for Apache Flink
> runtime 1.20.

#### External dependencies

The application expects 4 Kinesis Data Streams.

The stream names are defined in the configuration (see [below](#runtime-configuration)).
The application defines no default name and region. 
The [configuration for local development](./application_properties.json) set them, by default, to: 


* `SlidingWindowProcessingTimeOutput`, `us-east-1`.
* `SlidingWindowEventTimeOutput`, `us-east-1`.
* `TumblingWindowProcessingTimeOutput`, `us-east-1`.
* `TumblingWindowEventTimeOutput`, `us-east-1`.

Single-shard Streams in Provisioned mode will be sufficient for the emitted throughput.

#### IAM permissions

The application must have sufficient permissions to publish data to the Kinesis Data Streams.

When running locally, you need active valid AWS credentials that allow publishing data to the Streams.

---

### Runtime configuration

* **Local development**: uses the local file [application_properties.json](./application_properties.json)
* **On Amazon Managed Service for Apache Fink**: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

For this application, the configuration properties to specify :

| Group ID        | Key           | Mandatory | Example Value (default for local)    | Notes                                                             |
|-----------------|---------------|-----------|--------------------------------------|-------------------------------------------------------------------|
| `OutputStream0` | `stream.name` | Y         | `TumblingWindowProcessingTimeOutput` | Output stream for Tumbing Windowing in Procesing Time.            |
| `OutputStream0` | `aws.region`  | Y         | `us-east-1`                          | Region for the Tumbing Windowing in Procesing Time output stream. |
| `OutputStream1` | `stream.name` | Y         | `TumblingWindowEventTimeOutput`      | Output stream for Tumbing Windowing in Event Time.                |
| `OutputStream1` | `aws.region`  | Y         | `us-east-1`                          | Region for the Tumbing Windowing in Event Time output stream.     |
| `OutputStream2` | `stream.name` | Y         | `SlidingWindowProcessingTimeOutput`  | Output stream for Sliding Windowing in Procesing Time.            |
| `OutputStream2` | `aws.region`  | Y         | `us-east-1`                          | Region for the Sliding Windowing in Procesing Time output stream. |
| `OutputStream3` | `stream.name` | Y         | `SlidingWindowEventTimeOutput`       | Output stream for Sliding Windowing in Event Time.                |
| `OutputStream3` | `aws.region`  | Y         | `us-east-1`                          | Region for the Sliding Windowing in Event Time output stream.     |

In addition to these configuration properties, when running a PyFlink application in Managed Flink you need to set two
[Additional configuring for PyFink application on Managed Flink](#additional-configuring-for-pyfink-application-on-managed-flink).

---

### How to run and build the application

#### Local development - in the IDE

1. Make sure you have created the Kinesis Streams and you have a valid AWS session that allows you to publish to the Streams (the way of doing it depends on your setup)
2. Run `mvn package` once, from this directory. This step is required to download the jar dependencies - the Kinesis connector in this case
3. Set the environment variable `IS_LOCAL=true`. You can do from the prompt or in the run profile of the IDE
4. Run `main.py`

You can also run the python script directly from the command line, like `python main.py`. This still require running `mvn package` before.

If you are using Virtual Environments, make sure the to select the venv as a runtime in your IDE.

If you forget the set the environment variable `IS_LOCAL=true` or forget to run `mvn package` the application fails on start.

> 🚨 The application does not log or print anything. 
> If you do not see any output in the console, it does not mean the application is not running.
> The output is sent to the Kinesis streams. You can inspect the content of the streams using the Data Viewer in the Kinesis console

Note: if you modify the Python code, you do not need to re-run `mvn package` before running the application locally.

#### Deploy and run on Amazon Managed Service for Apache Flink

1. Make sure you have the 4 required Kinesis Streams
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to all the 4 Kinesis Streams
4. Package the application: run `mvn clean package` from this directory
5. Upload to an S3 bucket the zip file that the previous creates in the [`./target`](./target) subdirectory
6. Configure the Managed Flink application: set Application Code Location to the bucket and zip file you just uploaded
7. Configure the Runtime Properties of the application, creating the Group ID, Keys and Values as defined in the [application_properties.json](./application_properties.json)
8. Start the application
9. When the application transitions to "Ready" you can open the Flink Dashboard to verify the job is running, and you can inspect the data published to the Kinesis Streams, using the Data Viewer in the Kinesis console.

#### Publishing code changes to Amazon Managed Service for Apache Flink

Follow this process to make changes to the Python code

1. Modify the code locally (test/run locally, as required)
2. Re-run `mvn clean package` - **if you skip this step, the zipfile is not updated**, and contains the old Python script.
3. Upload the new zip file to the same location on S3 (overwriting the previous zip file)
4. In the Managed Flink application console, enter *Configure*, scroll down and press *Save Changes*
   * If your application was running when you published the change, Managed Flink stops the application and restarts it with the new code
   * If the application was not running (in Ready state) you need to click *Run* to restart it with the new code

> 🚨 by design, Managed Flink does not detect the new zip file automatically.
> You control when you want to restart the application with the code changes. This is done saving a new configuration from the 
> console or using the [*UpdateApplication*](https://docs.aws.amazon.com/managed-flink/latest/apiv2/API_UpdateApplication.html)
> API.

---

### Application structure

The application generates synthetic data using the [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/datagen/) connector.
No external data generator is required.

It demonstrates 4 types of windowing aggregations:

* Sliding Window based on processing time
* Sliding Window based on event time
* Tumbling Window based on processing time
* Tumbling Window based on event time

The result of each aggregation is sent to a different Kinesis Data Streams. 
You can inspect the results consuming the Streams or using the Data Viewer from the AWS Console.

---

### Application packaging and dependencies

This examples also demonstrate how to include jar dependencies - e.g. connectors - in a PyFlink application, and how to 
package it, for deploying on Amazon Managed Service for Apache Flink.

Any jar dependencies must be added to the `<dependencies>` block in the [pom.xml](pom.xml) file.
In this case, you can see we have included `flink-sql-connector-kinesis`

Executing `mvn package` takes care of downloading any defined dependencies and create a single "fat-jar" containing all of them.
This file, is generated in the `./target` subdirectory and is called `pyflink-dependencies.jar`

> The `./target` directory and any generated files are not supposed to be committed to git.

When running locally, for example in your IDE, PyFlink will look for this jar file in `./target`.

When you are happy with your Python code and you are ready to deploy the application to Amazon Managed Service for Apache Flink,
run `mvn package` **again**. The zip file you find in `./target` is the artifact to upload to S3, containing
both jar dependencies and your Python code.

#### Additional configuring for PyFink application on Managed Flink

To tell Managed Flink what Python script to run and the fat-jar containing all dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                     |
|---------------------------------------|-----------|-----------|--------------------------------|---------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.          |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies. |
