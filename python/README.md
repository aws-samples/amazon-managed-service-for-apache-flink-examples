## Flink Python examples

This folder contains examples of Flink applications written in Python.

---

### Packaging dependencies for running in Amazon Managed Service for Apache Flink

There multiple ways of packaging a PyFlink application with multiple dependencies, Python libraries or JAR dependencies, like connectors. [Amazon Managed Service for Apache Flink](https://aws.amazon.com/managed-service-apache-flink/) expects a specific packaging and runtime configuration for PyFlink applications.

#### JAR dependencies

Amazon Managed Service for Apache Flink expects **a single JAR file** containing all JAR dependencies of a PyFlink application. These dependencies include any Flink connector and any other Java library your PyFlink application requires. All these dependencies must be packaged in a single *uber-jar* using [Maven](https://maven.apache.org/) or other similar tools.

The [Getting Started](./GettingStarted/) example, and most of the other example in this directory, show a project set up that allows you to add any number of JAR dependencies to your PyFlink project. It requires Java JDK and [Maven](https://maven.apache.org/) to develop and to package the PyFlink application, and uses Maven to build the *uber-jar* and to package the PyFlink application in the `zip` file for deploymenbt on Managed Service for Apache Flink. This set up also allows you to run your application in your IDE, for debugging and development, and in Managed Service for Apache Flink **without any code changes**.


#### Python dependencies

In Apache Flink supports multiple ways of adding Python libraries to your PyFlink application.

Thre [Python Dependencies](./PythonDependencies/) example shows the most general way of adding any number of Python libraries to your application, using the `requriement.txt` file. This method works with any type of Python library, and does not require packaging these dependencies into the `zip` file deployed on Managed Service for Apache Flink. This also allows you to run the application in your IDE, for debugging and development, and in Managed Service for Apache Flink **without any code changes**.

---

### Python and Flink versions for local development

There are some known issues with some specific Python and PyFlink versions, for local development

* We recommend using **Python 3.11** to develop Python Flink 1.20.0 applications.
  This is also the runtime used by Amazon Managed Service for Apache Flink.
  Installation of the Python Flink 1.19 library on Python 3.12 may fail.
* Installation of the Python Flink **1.15** library on machines based on **Apple Silicon** fail. 
  We recommend upgrading to the Flink 1.20, 1.19 or 1.18. Versions 1.18+ work correctly also on Apple Silicon machines.
  If you need to maintain a Flink 1.15 application using a machine based on Apple Silicon, you can follow [the guide to develop Flink 1.15 on Apple Silicon](LocalDevelopmentOnAppleSilicon).


> None of these issues affects Python Flink applications running on Amazon Managed Service for Apache Flink.
> The managed service uses Python versions compatible with the Flink runtime version you choose.
