## Flink Python examples

This folder contains examples of Flink applications written in Python.

### Packaging dependencies for running in Amazon Managed Service for Apache Flink

There multiple ways of packaging a PyFlink application with multiple dependencies, Python libraries or JAR dependencies, like connectors.
[Amazon Managed Service for Apache Flink](https://aws.amazon.com/managed-service-apache-flink/) expects a specific packaging
and runtime configuration, for PyFlink applications.

In the [Getting Started](./GettingStarted) example, as most of the other examples in this directory, shows an effective way
packaging dependencies, allowing you to run the application both locally, in your IDE, and deployed on Amazon Managed Service for Apache Flink.

Note that packaging dependencies require installing Java JDK and [Maven](https://maven.apache.org/) on the development machine. 
Maven is used to "merge" multiple jar dependencies and to build the `.zip` file to deploy on Managed Flink.

### Python and Flink versions for local development

There are some known issues with some specific Python and PyFlink versions, for local development

* We recommend using **Python 3.11** to develop Python Flink 1.19.1 applications.
  This is also the runtime used by Amazon Managed Service for Apache Flink.
  Installation of the Python Flink 1.19 library on Python 3.12 may fail.
* Installation of the Python Flink **1.15** library on machines based on **Apple Silicon** fail. 
  We recommend upgrading to the Flink 1.19 or 1.18. Versions 1.18+ work correctly also on Apple Silicon machines.
  If you need to maintain a Flink 1.15 application using a machine based on Apple Silicon, you can follow [the guide to develop Flink 1.15 on Apple Silicon](LocalDevelopmentOnAppleSilicon).


> None of these issues affects Python Flink applications running on Amazon Managed Service for Apache Flink.
> The managed service uses Python versions compatible with the Flink runtime version you choose.
