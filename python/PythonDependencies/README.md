## Packaging Python dependencies

Examples showing how to include Python libraries in your PyFlink application.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: Kinesis Connector
* Language: Python

This example demonstrate how to include in your PyFlink application additional Python libraries.

There are multiple ways of adding Python dependencies to an application deployed on Amazon Managed Service for Apache Flink.
The approach demonstrated in this example has several benefits:

* It works with any number of Python libraries
* It allows to run the application locally, in your machine, and in Managed Service for Apache Flink, with no code changes
* It supports Python libraries not purely written in Python, like PyArrow for example, that are specific to a CPU architecture. 
  Including these libraries may be challenging because they architecture of your development machine may be different from
  the architecture of Managed Service for Apache Flink.

As many other examples, this example also packages any JAR dependencies required for the application. In this case the Kinesis
connector.

The application is very simple, and uses _botocore_ and _boto3_ Python libraries.
These libraries are used to invoke Amazon Bedrock to get a fun fact about a random number.
The output is sent to a Kinesis Data Stream.


---

### Requirements

#### Development and build environment requirements

* Python 3.11
* PyFlink library: `apache-flink==1.20.0` + any libraries defined in `requirements.txt`
* Java JDK 11+ and Maven

> ⚠️ The Flink Python library 1.20.0 may fail installing on Python 3.12.
> We recommend using Python 3.11 for development, the same Python version used by Amazon Managed Service for Apache Flink
> runtime 1.20.

> JDK and Maven are uses to download and package any required Flink dependencies, e.g. connectors, and
  to package the application as `.zip` file, for deployment to Amazon Managed Service for Apache Flink.

#### External dependencies

The application uses a Bedrock model you can configure and a Kinesis Data Stream.

Make sure you enabled the Bedrock model, in the region you chose, and you have created the Kinesis Stream.

The stream names are defined in the configuration (see [below](#runtime-configuration)).
The application defines no default name and region. 
The [configuration for local development](./application_properties.json) set them, by default, to: `ExampleOutputStream`, `us-east-1`.

#### IAM permissions

The application must have sufficient permissions to invoke the Bedrock model you configured, and to publish data to the 
Kinesis Data Stream.

When running locally, you need active valid AWS credentials that allow invoking the model and publishing data to the Stream.

---

### Runtime configuration

* **Local development**: uses the local file [application_properties.json](./application_properties.json)
* **On Amazon Managed Service for Apache Fink**: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

For this application, the configuration properties to specify are:


| Group ID         | Key           | Mandatory | Example Value (default for local) | Notes                         |
|------------------|---------------|-----------|-----------------------------------|-------------------------------|
| `Bedrock`        | `model.id`    | Y         | `ai21.j2-mid-v1`                  | Region of Bedrock.            |
| `Bedrock`        | `aws.region`  | Y         | `us-east-1`                       | Region of Bedrock.            |
| `OutputStream0`  | `stream.name` | Y         | `ExampleOutputStream`             | Output stream name.           |
| `OutputStream0`  | `aws.region`  | Y         | `us-east-1`                       | Region for the output stream. |

In addition to these configuration properties, when running a PyFlink application in Managed Flink you need to set two
[Additional configuring for PyFink application on Managed Flink](#additional-configuring-for-pyfink-application-on-managed-flink).

---

### How to run and build the application

#### Installing Python dependencies in your development environment environment

> We recommend to use Virtual Environments or any equivalent tool to create isolated environment

To run the application locally, from the command line or in your IDE, you need to install the following Python dependencies:
* `apache-flink==1.20.0`
* Any requirements defined in `requirements.txt`

Assuming you use `virtualenv` and you have it installed already:

1. Create the Virtual Environment in the project directory: `virtualenv venv`
2. Activate the Virtual Environment you just created: `source venv/bin/activate`
3. Install PyFlink library: `pip install apache-flink==1.20.0`
4. Install other dependencies from `requirements.txt`: `pip install -r requirements.txt`


#### Local development - in the IDE

1. Make sure you have created the Kinesis Streams and you have a valid AWS session that allows you to publish to the 
  Streams (the way of doing it depends on your setup)
2. If you are using Virtual Environments, make sure your IDE uses the venv you created. Follow the documentations of your
  IDE ([PyCharm](https://www.jetbrains.com/help/pycharm/creating-virtual-environment.html), [Visual Studio Code](https://code.visualstudio.com/docs/python/environments))
3. Run `mvn package` once, from this directory. This step is required to download the jar dependencies - the Kinesis connector in this case
4. Set the environment variable `IS_LOCAL=true`. You can do from the prompt or in the run profile of the IDE
5. Run `main.py`

You can also run the python script directly from the command line, like `python main.py`. 
This still require running `mvn package` before.

If you forget the set the environment variable `IS_LOCAL=true` or forget to run `mvn package` the application fails on start.

> 🚨 The application does not log or print anything. 
> If you do not see any output in the console, it does not mean the application is not running.
> The output is sent to the Kinesis streams. You can inspect the content of the streams using the Data Viewer in the Kinesis console

Note: if you modify the Python code, you do not need to re-run `mvn package` before running the application locally.

#### Deploy and run on Amazon Managed Service for Apache Flink

1. Make sure you have the required Kinesis Streams and you have activated the Bedrock model
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to the Kinesis Streams and invoke the Bedrock model
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

The application generates synthetic data using the [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/connectors/table/datagen/) connector.
No external data generator is required.

The application then invokes a Bedrock model, on every message, to generate some random quotes and write the result to 
Kinesis Data Streams.

Invoking Bedrock is used purely to demonstrate how write and package a PyFlink applications requiring 3rd-party Python
libraries, boto3 and botocore in this case.

---

### Application packaging and dependencies

This example also demonstrates how to include both jar dependencies - e.g. connectors - and Python libraries in a PyFlink
application. It demonstrates how to package it for deploying on Amazon Managed Service for Apache Flink.

#### Jar dependencies

Any jar dependencies must be added to the `<dependencies>` block in the [pom.xml](pom.xml) file.
In this case, you can see we have included `flink-sql-connector-kinesis`

Executing `mvn package` takes care of downloading any defined dependencies and create a single "fat-jar" containing all of them.
This file, is generated in the `./target` subdirectory and is called `pyflink-dependencies.jar`

> The `./target` directory and any generated files are not supposed to be committed to git.

When running locally, for example in your IDE, PyFlink will look for this jar file in `./target`.

When you are happy with your Python code and you are ready to deploy the application to Amazon Managed Service for Apache Flink,
run `mvn package` **again**. The zip file you find in `./target` is the artifact to upload to S3, containing
both jar dependencies and your Python code.

#### Python 3rd-party libraries

Any additional 3rd-party Python library (i.e. Python libraries not provided by PyFlink directly) must also be available
when the application runs.

There are different approaches for including these libraries in an application deployed on Managed Service for Apache Flink.
The approach demonstrated in this example is the following:

1. Define a `requirements.txt` with all additional Python dependencies - **DO NOT include any PyFlink dependency**
2. Register the `requirements.txt` file in the Flink runtime environment, when the application starts. Flink will automatically
   install any of these dependencies not already available in the runtime. This happens in the following lines of the application:
  ```python
  python_source_dir = str(pathlib.Path(__file__).parent)
  table_env.set_python_requirements(requirements_file_path="file:///" + python_source_dir + "/requirements.txt")
  ```

With this approach the Python library are **not packaged** with the application artifact you deploy to Managed Service 
for Apache Flink. They are installed by the runtime on the cluster, when the application starts.

#### Additional configuring for PyFink application on Managed Flink

To tell Managed Flink what Python script to run and the fat-jar containing all dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                     |
|---------------------------------------|-----------|-----------|--------------------------------|---------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.          |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies. |

