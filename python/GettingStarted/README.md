## Getting Started Flink Python project

Sample PyFlink application reading from and writing to Kinesis Data Stream.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: Kinesis Connector
* Language: Python

This example provides the basic skeleton for a PyFlink application.

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


> ⚠️ As of 2024-06-27, the Flink Python library 1.19.x may fail installing on Python 3.12.
> We recommend using Python 3.11 for development, the same Python version used by Amazon Managed Service for Apache Flink
> runtime 1.20.

> JDK and Maven are used to download and package any required Flink dependencies, e.g. connectors, and
  to package the application as `.zip` file, for deployment to Amazon Managed Service for Apache Flink.

#### External dependencies

The application expects 2 Kinesis Data Streams.

The stream names are defined in the configuration (see [below](#runtime-configuration)).
The application defines no default name and region. 
The [configuration for local development](./application_properties.json) set them, by default, to: 

* `ExampleInputStream`, `us-east-1`.
* `ExampleOutputStream`, `us-east-1`.


Single-shard Streams in Provisioned mode will be sufficient for the emitted throughput.

#### IAM permissions

The application must have sufficient permissions to publish data to the Kinesis Data Streams.

When running locally, you need active valid AWS credentials that allow publishing data to the Streams.

### Runtime configuration

 When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

 When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key                    | Mandatory | Example Value (default for local)    | Notes                         |
|-----------------|------------------------|-----------|--------------------------------------|-------------------------------|
| `InputStream0`  | `stream.name`          | Y         | `ExampleInputStream`                 | Input stream.                 |
| `InputStream0`  | `aws.region`           | Y         | `us-east-1`                          | Region for the input stream.  |
| `InputStream0`  | `flink.stream.initpos` | N         | `LATEST`                             | Start position in the input stream. `LATEST` by default |
| `OutputStream0` | `stream.name`          | Y         | `TumblingWindowProcessingTimeOutput` | Output stream .               |
| `OutputStream0` | `aws.region`           | Y         | `us-east-1`                          | Region for the output stream.        |


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

##### Troubleshooting the application when running locally

By default, the PyFlink application running locally does not send logs to the console. 
Any exception thrown by the Flink runtime (i.e. not due to Python error) will not appear in the console. 
The application may appear to be running, but actually continuously failing and restarting.

To see any error messages, you need to inspect the Flink logs.
By default, PyFlink will send logs to the directory where the PyFlink module is installed (Flink home).
Use this command to find the directory:

```
$ python -c "import pyflink;import os;print(os.path.dirname(os.path.abspath(pyflink.__file__))+'/log')"
```


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

##### Troubleshooting Python errors when the application runs on Amazon Managed Service for Apache Flink

Amazon Managed Service for Apache Flink sends all logs to CloudWatch Logs.
You can find the name of the Log Group and Log Stream in the configuration of the application, in the console.

Errors caused by the Flink engine are usually logged as `ERROR` and easy to find. However, errors reported by the Python 
runtime are **not** logged as `ERROR`.

Apache Flink logs any entry reported by the Python runtime using a logger named `org.apache.flink.client.python.PythonDriver`.

The easiest way to find errors reported by Python is using CloudWatch Insight, and run the following query:]

```
fields @timestamp, message
| sort @timestamp asc
| filter logger like /PythonDriver/
| limit 1000
```

> 🚨 If the Flink jobs fails to start due to an error reported by Python, for example a missing expected configuration 
> parameters, the Amazon Managed Service for Apache Flink may report as *Running* but the job fails to start.
> You can check whether the job is actually running using the Apache Flink Dashboard. If the job is not listed in the 
> Running Job List, it means it probably failed to start due to an error.
> 
> In CloudWatch Logs you may find an `ERROR` entry with not very explanatory message "Run python process failed". 
> To find the actual cause of the problem, run the CloudWatch Insight above, to see the actual error reported by 
> the Python runtime.


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

### Generating sample data

Use the [Python script](../data-generator/) provided, to generate sample stock data to Kinesis Data Stream.

---

### Application structure

The application consumes data from a Kinesis source and publishes without any modification to a Kinesis sink.

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
