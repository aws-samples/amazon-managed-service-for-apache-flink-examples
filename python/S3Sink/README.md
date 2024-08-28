## Example of writing to S3 in PyFlink 

Example showing a PyFlink application writing to S3.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: FileSystem (S3)
* Language: Python

The application demonstrates setting up a sink writing JSON files to S3.

The application is written in Python, but operators are defined using SQL.
This is a popular way of defining applications in PyFlink, but not the only one. You could attain the same results
using Table API ar DataStream API, in Python.

The job can run both on Amazon Managed Service for Apache Flink, and locally for development.

### Requirements

#### Development and build environment requirements

* Python 3.11
* PyFlink library: `apache-flink==1.20.0`
* Java JDK 11+ and Maven


> ⚠️ As of 2024-06-27, the Flink Python library 1.19.x may fail installing on Python 3.12.
> We recommend using Python 3.11 for development, the same Python version used by Amazon Managed Service for Apache Flink
> runtime 1.20.

> JDK and Maven are required to download and package any required Flink dependencies, e.g. connectors, and
  to package the application as `.zip` file, for deployment to Amazon Managed Service for Apache Flink.

#### External dependencies

The application requires an S3 bucket to write data to.

The name of the output bucket must be specified in the application configuration (see [below](#runtime-configuration)).

#### IAM permissions

The application must have sufficient permissions to write data to the destination S3 bucket.

When running locally, you need active valid AWS credentials that allow publishing writing to the bucket.

## Runtime configuration

* **Local development**: uses the local file [application_properties.json](./application_properties.json) - **Edit the file with your output bucket name**
* **On Amazon Managed Service for Apache Fink**: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

For this application, the configuration properties to specify are:


| Group ID       | Key      | Mandatory | Example Value    | Default for local development | Notes                                                  |
|----------------|----------|-----------|------------------|-------------------------------|--------------------------------------------------------|
| `bucket`       | `name`   | Y         | `my-bucket-name` | no default                    | Name of the destination bucket, without `s3://` prefix |


In addition to these configuration properties, when running a PyFlink application in Managed Flink you need to set two
[Additional configuring for PyFink application on Managed Flink](#additional-configuring-for-pyfink-application-on-managed-flink).

> If you forget to edit the local `application_properties.json` configuration to point your destination bucket, the application will fail to start locally.

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


##### 🚨 Install local Flink dependencies

To run locally, PyFlink requires you to download the 
[S3 File System Hadoop plugin](https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/filesystems/s3/) 
and copy it in the directory where PyFlink is installed.

1. To find the PyFlink home directory run the following command (if you are using Virtual Environments, make sure the environment is activated before running this command):
   ```
   $ python -c "import pyflink;import os;print(os.path.dirname(os.path.abspath(pyflink.__file__)))"
   ```
2. For Flink 1.20, download `flink-s3-fs-hadoop-1.20.0.jar` (the latest as of 2024-08-27)
   from [this link](https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/1.20.0/flink-s3-fs-hadoop-1.20.0.jar).
   If you are using e different Flink version, download the plugin for the correct version 
   (see [available plugin versions](https://mvnrepository.com/artifact/org.apache.flink/flink-s3-fs-hadoop)).
3. Copy it into the `<flink-home>/lib/` directory

> Note: [Flink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/deployment/filesystems/plugins/#file-systems) 
> currently contains an error, stating that you need to install this dependency in the `<flink-home>/plugins` directory instead.


##### Troubleshooting when running the application locally

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

1. Make sure you have the destination bucket
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to the destination bucket
4. Package the application: run `mvn clean package` from this directory
5. Upload to an S3 bucket the zip file that the previous creates in the [`./target`](./target) subdirectory
6. Configure the Managed Flink application: set Application Code Location to the bucket and zip file you just uploaded
7. Configure the Runtime Properties of the application, creating the Group ID, Keys and Values as defined in the [application_properties.json](./application_properties.json)
8. Start the application
9. When the application transitions to "Ready" you can open the Flink Dashboard to verify the job is running, and you can inspect the data published to S3 bucket.

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

Generated records are written to a destination table, writing to S3.

Data format is JSON, and partitioning is by the `sensor_id`.

Note that the FileSink connector writes to S3 on checkpoint. For this reason, when running locally checkpoint is set up
programmatically by the application every minute. When deployed on Amazon Managed Service for Apache Flink, the checkpoint
configuration is configured as part of the Managed Flink application configuration. By default, it's every minute.

If you disable checkpoints (or forget to set it up when running locally) the application runs but never writes any file to S3.

---

### Application packaging and dependencies

This examples also demonstrate how to include jar dependencies - e.g. connectors - in a PyFlink application, and how to 
package it, for deploying on Amazon Managed Service for Apache Flink.

Any jar dependencies must be added to the `<dependencies>` block in the [pom.xml](pom.xml) file.
In this case, you can see we have included `flink-s3-fs-hadoop` to support the S3 (`s3a://`) file system.

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
