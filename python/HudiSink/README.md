## Example of writing to Apache Hudi with PyFlink

Example showing a PyFlink application writing to Hudi table in S3.

* Flink version: 1.20
* Flink API: Table API & SQL
* Flink Connectors: Apache Hudi & Flink S3
* Language: Python

This application demonstrates setting up Apache Hudi table as sink.

The application is written in Python, but operators are defined using SQL. This is a popular way of defining applications in PyFlink, but not the only one. You could attain the same results using Table API ar DataStream API, in Python.

The job can run both on Amazon Managed Service for Apache Flink, and locally for development.
---

### Dependency Management

This project uses Maven Shade Plugin with extensive exclusions to handle dependency conflicts and optimize the application size. The Hudi connector requires careful dependency management due to its complex ecosystem.

---

## Excluded Java Versions in maven-shade-plugin

Apache Flink 1.20 [only supports Java 11 as non-experimental](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/deployment/java_compatibility/). 
Amazon Managed Service for Apache Flink currently runs on Java 11. 
Any Java code must be compiled with a target java version 11.

We have excluded certain Java versions to avoid errors caused by [multi-release JARs](https://openjdk.org/jeps/238) where the META-INF/versions/XX directories contain **Java version-specific class files**.

Example: A dependency might include optimized code for Java 21+ in META-INF/versions/21/....

### Why Exclude Specific Versions

*  Avoid Compatibility Issues - If you include JAR dependencies compiled for Java 21/22 you may face errors like  *java.lang.UnsupportedClassVersionError: Unsupported major.minor version 65* 
* Prevent Conflicts - Some libraries include multi-release JARs that conflict with Flink's dependencies when merged into a fat JAR.

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

> This code has been tested and compiled using OpenJDK 11.0.22 and Maven 3.9.6

#### External dependencies

The application requires Amazon S3 bucket to write the Hudi table data. The application needs appropriate permissions for Amazon S3 as [discussed below](#iam-permissions).

The target Hudi table parameters are defined in the configuration (see [below](#runtime-configuration)).

> **Local Development**: When running locally with `IS_LOCAL=true`, no external dependencies are required as the application uses local filesystem storage.

#### IAM permissions

The application must have sufficient permissions to read and write to Amazon S3 bucket where Hudi table data and metadata will be stored.

> **Local Development**: When running locally with `IS_LOCAL=true`, no AWS credentials or IAM permissions are required as the application uses local filesystem storage.

### Runtime configuration

* **Local development**: uses the local file [application_properties.json](./application_properties.json) - **Edit the file with your Hudi table details which includes warehouse location, database name, table name, and AWS region**
* **On Amazon Managed Service for Apache Fink**: define Runtime Properties, using Group ID and property names based on the content of [application_properties.json](./application_properties.json)

For this application, the configuration properties to specify are:

Runtime parameters:

| Group ID        | Key             | Mandatory | Example Value (default for local) | Notes                                            |
|-----------------|-----------------|-----------|-----------------------------------|--------------------------------------------------|
| `HudiTable0`    | `catalog.name`  | Y         | `glue_catalog`                    | Catalog name (used for configuration consistency, not for Glue integration) |
| `HudiTable0`    | `warehouse.path`| Y         | `s3a://my_bucket/my_warehouse`    | Warehouse path for Hudi table. **Important** - Use `s3a://` protocol |
| `HudiTable0`    | `database.name` | Y         | `my_database`                     | Database name for Hudi table                    |
| `HudiTable0`    | `table.name`    | Y         | `my_table`                        | Table name to write the data                     |
| `HudiTable0`    | `aws.region`    | Y         | `us-east-1`                       | AWS region for S3A filesystem configuration (used in production, ignored in local mode). |


In addition to these configuration properties, when running a PyFlink application in Managed Flink you need to set two
[Additional configuring for PyFink application on Managed Flink](#additional-configuring-for-pyfink-application-on-managed-flink).

> If you forget to edit the local `application_properties.json` configuration to point your Hudi table and warehouse path, the application will fail to start locally.

#### Additional configuring for PyFink application on Managed Flink

To tell Managed Flink what Python script to run and the fat-jar containing all dependencies, you need to specific some
additional Runtime Properties, as part of the application configuration:

| Group ID                              | Key       | Mandatory | Value                          | Notes                                                                     |
|---------------------------------------|-----------|-----------|--------------------------------|---------------------------------------------------------------------------|
| `kinesis.analytics.flink.run.options` | `python`  | Y         | `main.py`                      | The Python script containing the main() method to start the job.          |
| `kinesis.analytics.flink.run.options` | `jarfile` | Y         | `lib/pyflink-dependencies.jar` | Location (inside the zip) of the fat-jar containing all jar dependencies. |

> ⚠️ If you forget adding these parameters to the Runtime properties, the application will not start.
---

### How to run and build the application

#### Local development - in the IDE

1. Run `mvn package` once, from this directory. This step is required to download the jar dependencies - the Hudi connector in this case
2. Set the environment variable `IS_LOCAL=true`. You can do from the prompt or in the run profile of the IDE
3. Run `main.py`

> **Note for Local Development**: When running locally with `IS_LOCAL=true`, the application automatically uses a local filesystem instead of S3 to avoid AWS credential requirements. The Hudi table will be created in a temporary directory on your local machine. This allows for easy testing without needing AWS credentials or S3 access.
>
> **Why HudiSink requires local filesystem for testing**: Unlike some other connectors, Hudi requires AWS credentials even during table creation because it needs to check if the table already exists by reading metadata from S3 (specifically the `.hoodie/hoodie.properties` file). This happens before any data is written. To provide a simple local testing experience, HudiSink automatically switches to local filesystem when `IS_LOCAL=true` is set.

You can also run the python script directly from the command line, like `IS_LOCAL=true python main.py`. This still require running `mvn package` before.

If you are using Virtual Environments, make sure the to select the venv as a runtime in your IDE.

If you forget the set the environment variable `IS_LOCAL=true` or forget to run `mvn package` the application fails on start.

> 🚨 The application does not log or print anything. 
> If you do not see any output in the console, it does not mean the application is not running.
> The output is sent to the Hudi table. You can inspect the content of the table using Amazon Athena or Trino/Spark on EMR.

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

1. Make sure you have the S3 bucket location for Hudi warehouse path
2. Create a Managed Flink application
3. Modify the application IAM role to allow writing to S3 location for the Hudi table
4. Package the application: run `mvn clean package` from this directory
5. Upload to an S3 bucket the zip file that the previous creates in the [`./target`](./target) subdirectory
6. Configure the Managed Flink application: set Application Code Location to the bucket and zip file you just uploaded
7. Configure the Runtime Properties of the application, creating the Group ID, Keys and Values as defined in the [application_properties.json](./application_properties.json)
8. Start the application
9. When the application transitions to "Ready" you can open the Flink Dashboard to verify the job is running, and you can inspect the data published to the Hudi table using S3 directly or query tools like Athena or Trino/Spark on EMR.

##### Troubleshooting Python errors when the application runs on Amazon Managed Service for Apache Flink

Amazon Managed Service for Apache Flink sends all logs to CloudWatch Logs.
You can find the name of the Log Group and Log Stream in the configuration of the application, in the console.

Errors caused by the Flink engine are usually logged as `ERROR` and easy to find. However, errors reported by the Python 
runtime are **not** logged as `ERROR`.

Apache Flink logs any entry reported by the Python runtime using a logger named `org.apache.flink.client.python.PythonDriver`.

The easiest way to find errors reported by Python is using CloudWatch Insight, and run the following query:

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

### Local Development vs Production Differences

#### Why HudiSink uses local filesystem for local testing

**The Challenge:**
Unlike other connectors (like IcebergSink), Apache Hudi requires immediate access to storage during table creation to:

1. **Check existing table metadata**: Hudi reads `.hoodie/hoodie.properties` from the target path to determine if a table already exists
2. **Validate table schema**: Hudi needs to verify schema compatibility with existing tables
3. **Initialize table metadata**: Even for new tables, Hudi immediately writes metadata files

**Technical Details:**
- **Hudi Behavior**: Calls `StreamerUtil.getTableConfig()` during table creation, which immediately tries to read from S3
- **Error without credentials**: `org.apache.hudi.exception.HoodieIOException: Get table config error` occurs when AWS credentials are not available

**Our Solution:**
The application includes smart protocol handling:
- **Auto-converts `s3://` to `s3a://`**: If you accidentally configure `s3://` in your properties, the application automatically converts it to `s3a://` (required by Hudi)
- **Local development override**: When `IS_LOCAL=true` is set, automatically switches to `file://` protocol using a temporary local directory
- **Maintains identical Hudi functionality**: All Hudi features work the same way in local testing
- **Eliminates AWS credential requirements**: No AWS setup needed for local development

**Alternative Approaches:**
If you prefer to test with actual S3 access locally, you can:
1. Configure AWS credentials (`aws configure` or environment variables)
2. Remove the local filesystem override in `main.py`
3. Ensure your AWS credentials have S3 permissions

---

### Application structure

The application generates synthetic data using the [DataGen](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/datagen/) connector.
No external data generator is required.

Generated records are written to a destination Hudi table with data stored in S3.

Data format is Parquet, and partitioning is by the `sensor_id`.

Note that the Hudi connector writes to S3 on checkpoint. For this reason, when running locally checkpoint is set up programmatically by the application every minute. When deployed on Amazon Managed Service for Apache Flink, the checkpoint
configuration is configured as part of the Managed Flink application configuration. By default, it's every minute.

If you disable checkpoints (or forget to set it up when running locally) the application runs but never writes any data to S3.

---

### Application packaging and dependencies

This examples also demonstrate how to include jar dependencies - e.g. connectors - in a PyFlink application, and how to 
package it, for deploying on Amazon Managed Service for Apache Flink.

Any jar dependencies must be added to the `<dependencies>` block in the [pom.xml](pom.xml) file.
In this case, you can see we have included `hudi-flink-bundle`, Hadoop dependencies, and AWS SDK components for Hudi sink to work with Flink.

Executing `mvn package` takes care of downloading any defined dependencies and create a single "fat-jar" containing all of them.
This file, is generated in the `./target` subdirectory and is called `pyflink-dependencies.jar`

> The `./target` directory and any generated files are not supposed to be committed to git.

When running locally, for example in your IDE, PyFlink will look for this jar file in `./target`.

When you are happy with your Python code and you are ready to deploy the application to Amazon Managed Service for Apache Flink,
run `mvn package` **again**. The zip file you find in `./target` is the artifact to upload to S3, containing both jar dependencies and your Python code.