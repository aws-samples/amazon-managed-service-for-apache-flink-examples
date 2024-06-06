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
 
### Developing PyFlink 1.15 on Apple Silicon macOS

> 🚨 This limitation only applies to Flink 1.15, and only for local development. 
> With Flink 1.18 or later you can install PyFlink libraries on your macOS machine as any other Python library. 
> See the [Getting Started](./GettingStarted) for more details.

PyFlink version 1.15 has [known issues](https://issues.apache.org/jira/browse/FLINK-25188) on macOS machines using Apple Silicon chips (e.g. M1) due to Python dependencies.
You cannot install the `apache-pyflink==1.15.4` library on the machine, directly. This make it difficult to develop PyFlink 1.15 applications on Apple Silicon.

A workaround is running the Python interpreter in a Docker container built for Intel architecture, and running it in the M1 machine using the Rosetta emulation.

You can find the [step-by-step instructions to for PyFlink local development using docker on Apple Silicon machines](./LocalDevelopmentOnAppleSilicon).

