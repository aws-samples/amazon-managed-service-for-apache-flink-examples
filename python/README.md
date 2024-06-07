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
