## Packaging Python dependencies with the ZIP

Example showing how you can package Python dependencies with the ZIP file you upload to S3.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: Kinesis Connector
* Language: Python

This example shows how you can package Python dependencies within the ZIP and make them available to the application.

> This method is alternative to what illustrated in the [Python Dependencies](../PythonDependencies) example which relies on the
`requirements.txt` file for installing the dependencies at runtime.

The approach shown in this example has the following benefits:

* It works with any number of Python libraries
* It supports Python libraries which include **native dependencies**, such as SciPy or Pydantic specific to the CPU architecture (note that Pandas, NumPy, and PyArrow also have native dependencies, but are already available as transitive dependencies 
of `apache-flink` and should not be added as additional dependencies).
* It allows to run the application locally, in your machine, and in Managed Service for Apache Flink, with no code changes.
* Dependencies are available both during job initialization, in the `main()` method, and for data processing, for example in a User Defined Function (UDF).

Drawbacks:
* You need to use a virtual environment for the Python dependencies when running locally, because the CPU architecture of your machine may differ from the architecture used by Managed Service for Apache Flink
* Python dependencies are included in the ZIP file slowing down a bit operations

For more details about how packaging dependencies works, see [Packaging application and dependencies](#packaging-application-and-dependencies), below.



The application includes [SciPy](https://scipy.org/) used in a UDF. The actual use is not important.
It also shows how the same library can be used during the job initialization.

The application generates random data using [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/datagen/)
and send the output to a Kinesis Data Streams.

---

### How to run and build the application

#### Prerequisites

We recommend to use a Virtual environment (venv) on the development machine.

Development and build environment requirements:
* Python 3.11
* PyFlink library: `apache-flink==1.20.0`
* Java JDK 11+ and Maven


#### Setting up the local environment

To run the application locally, from the command line or in your IDE, you need to install the following Python dependencies:

* `apache-flink==1.20.0`
* Any additional Python dependency is defined in `requirements.txt`

Assuming you use virtualenv:

1. Create the Virtual Environment in the project directory: `virtualenv venv`
2. Activate the Virtual Environment you just created: `source venv/bin/activate`
3. Install PyFlink library: `pip install apache-flink==1.20.0`
4. Define the additional dependencies
   * Add JAR-dependencies in the `pom.xml` file
   * Add Python dependencies in the `requirements.txt` (do not include any Flink dependency!)
5. Install Python from `requirements.txt` into the venv, for local development: `pip install -r requirements.txt`
6. Download the Python dependencies for the target architecture: `pip install -r requirements.txt --target=dep/ --platform=manylinux2014_x86_64 --only-binary=:all:`
7. Run `mvn package` to download and package the JAR dependencies and build the ZIP artifact

> ⚠️ The Flink Python library 1.20.0 may fail installing on Python 3.12. We recommend using Python 3.11 for development, the same Python version used by Amazon Managed Service for Apache Flink runtime 1.20.

> JDK and Maven are uses to download and package any required Flink dependencies, e.g. connectors, and to package the application as .zip file, for deployment to Amazon Managed Service for Apache Flink.

### Runtime configuration

* **Local development**: uses the local file [application_properties.json](./application_properties.json)
* **On Amazon Managed Service for Apache Fink**: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

For this application, the configuration properties to specify are:


| Group ID         | Key           | Mandatory | Example Value (default for local) | Notes                         |
|------------------|---------------|-----------|-----------------------------------|-------------------------------|
| `OutputStream0`  | `stream.name` | Y         | `ExampleOutputStream`             | Output stream name.           |
| `OutputStream0`  | `aws.region`  | Y         | `us-east-1`                       | Region for the output stream. |



To tell Managed Flink what Python script to run, the fat-jar containing all dependencies, and the Python dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                              |
|---------------------------------------|-----------|-----------|--------------------------------|------------------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.                   |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies.          |
| `kinesis.analytics.flink.run.options` | `pyFiles` | Y         | `dep/`                         | Relative path of the subdirectory (inside the zip) containing Python dependencies. |

Note that these properties are ignored when running locally.

---

### Local development - in the IDE

1. Make sure you have created the Kinesis Streams and you have a valid AWS session that allows you to publish to the Streams (the way of doing it depends on your setup)
2. Make sure your IDE uses the venv you created. Follow the documentations of your IDE (PyCharm, Visual Studio Code)
3. Run `mvn package` once, from this directory. This step is required to download the jar dependencies - the Kinesis connector in this case
4. Set the environment variable `IS_LOCAL=true`. You can do from the prompt or in the run profile of the IDE
5. Run `main.py`

You can also run the python script directly from the command line, like python main.py. This still require running mvn package before.

If you forget the set the environment variable `IS_LOCAL=true` or forget to run `mvn package` the application fails on start.

> 🚨 The application does not log or print anything. If you do not see any output in the console, it does not mean the application is not running. The output is sent to the Kinesis streams. You can inspect the content of the streams using the Data Viewer in the Kinesis console



### Deploy and run on Amazon Managed Service for Apache Flink

1. Make sure you have the required Kinesis Streams
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to the Kinesis Stream
4. If you haven't done already, download the Python dependencies for the target architecture: `pip install -r requirements.txt --target=dep/ --platform=manylinux2014_x86_64 --only-binary=:all:`
5. Package the application: run `mvn clean package` from this directory
6. Upload to an S3 bucket the zip file that the previous creates in the ./target subdirectory
7. Configure the Managed Flink application: set Application Code Location to the bucket and zip file you just uploaded
8. Configure the Runtime Properties of the application, creating the Group ID, Keys and Values as defined in the [`application_properties.json`](application_properties.json) (a)
9. Start the application 
10. When the application transitions to "RUNNING" you can open the Flink Dashboard to verify the job is running, and you can inspect the data published to the Kinesis Streams, using the Data Viewer in the Kinesis console.



### Publishing code changes to Amazon Managed Service for Apache Flink

Follow this process to make changes to the Python code or the dependencies

1. Modify the code locally (test/run locally, as required)
2. Re-run `mvn clean package` - if you skip this step, the zipfile is not updated, and contains the old Python script.
3. Upload the new zip file to the same location on S3 (overwriting the previous zip file)
4. In the Managed Flink application console, enter Configure, scroll down and press Save Changes
    * If your application was running when you published the change, Managed Flink stops the application and restarts it with the new code
    * If the application was not running (in Ready state) you need to click Run to restart it with the new code


> 🚨 by design, Managed Flink does not detect the new zip file automatically. You control when you want to restart the application with the code changes. This is done saving a new configuration from the console or using the UpdateApplication API.

---

### Packaging application and dependencies


This example also demonstrates how to include both jar dependencies - e.g. connectors - and Python libraries in a PyFlink application. It demonstrates how to package it for deploying on Amazon Managed Service for Apache Flink.

The [`assembly/assembly.xml`](assembly/assembly.xml) file instructs Maven for including the correct files in the ZIP-file.  

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
2. Download the dependencies for the target architecture (`manylinux2014_x86_64`) into the `dep/` sub-folder
3. Package the `dep/` sub-folder in the ZIP file
4. At runtime, register the dependency folder. There are two **alternative** methods (use one of the following, not both):
   1. (recommended) Use the Managed Flink application configuration
      * Group ID: `kinesis.analytics.flink.run.options`
      * Key: `pyFiles`
      * Value: `dep/`
   2. Alternatively, you can programmatically register the directory but only when not running locally
      ```python
      if not is_local:
          python_source_dir = str(pathlib.Path(__file__).parent)
          table_env.add_python_file(file_path="file:///" + python_source_dir + "/dep")
      ```

> This approach differs from what shown in the [Python Dependencies](../PythonDependencies) example because the Python dependencies
> are packaged within the ZIP. The `requirements.txt` file is NOT used to download the dependencies at runtime.
