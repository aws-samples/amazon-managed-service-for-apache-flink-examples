## Flink Python Examples

This folder contains examples of Flink applications written in Python.

---

### Packaging Dependencies for Amazon Managed Service for Apache Flink

There are multiple ways to package a PyFlink application with dependencies, including Python libraries and JAR dependencies like connectors. [Amazon Managed Service for Apache Flink](https://aws.amazon.com/managed-service-apache-flink/) expects specific packaging and runtime configuration for PyFlink applications.

#### JAR Dependencies

Amazon Managed Service for Apache Flink expects **a single JAR file** containing all JAR dependencies of a PyFlink application. These dependencies include any Flink connector and any other Java library your PyFlink application requires. All these dependencies must be packaged in a single *uber-jar* using [Maven](https://maven.apache.org/) or other similar tools.

The [Getting Started](./GettingStarted) example, and most of the other examples in this directory, show a project setup that allows you to add any number of JAR dependencies to your PyFlink project. 
It requires Java JDK and [Maven](https://maven.apache.org/) to develop and package the PyFlink application, and uses Maven to build the *uber-jar* and package the PyFlink application in the `zip` file for deployment on Managed Service for Apache Flink. 
This setup also allows you to run your application in your IDE for debugging and development, and in Managed Service for Apache Flink **without any code changes**. 
**No local Flink cluster is required** for development.

#### Python Dependencies

Apache Flink supports multiple ways of adding Python libraries to your PyFlink application. 
Check out these two examples to learn how to correctly package dependencies:

1. [PythonDependencies](./PythonDependencies): How to download Python dependencies at runtime using `requirements.txt`
2. [PackagedPythonDependencies](./PackagedPythonDependencies): How to package dependencies with the application artifact

> The patterns shown in these examples allow you to run the application locally and on Amazon Managed Service
> for Apache Flink **without any code changes**, regardless of the machine architecture you are developing with.


---

### Python and Flink Versions for Local Development

There are some known issues with specific Python and PyFlink versions for local development:

* We recommend using **Python 3.11** to develop PyFlink 1.20.0 applications.
  This is also the runtime used by Amazon Managed Service for Apache Flink.
  Installation of the PyFlink 1.19 library on Python 3.12 may fail.
* If you are using Flink **1.15 or earlier** and developing on a machine based on **Apple Silicon**, installing PyFlink locally may fail.
  This is a know issue not affecting the application deployed on Amazon Managed Service for Apache Flink.
  To develop on Apple Silicon follow [the guide to develop Flink 1.15 on Apple Silicon](LocalDevelopmentOnAppleSilicon).
