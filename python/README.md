## Flink Python examples

This folder contains examples of Flink applications written in Python

### Getting Started

For a comprehensive, step-by-step Getting Started using PyFlink on  on [Amazon Managed Service for Apache Flink](https://aws.amazon.com/managed-service-apache-flink/)
refer to **[PyFlink Getting Started](https://github.com/aws-samples/pyflink-getting-started)**. 
Step-by-step instructions to set up your local PyFlink development environment, and to deploy your Python application on
Amazon Managed Service for Apache Flink.

### Packaging multiple dependencies in Amazon Managed Service for Apache Flink

To learn how to package multiple dependencies in a PyFlink application to run on managed Flink, look at the [Firehose sink example](./FirehoseSink/).
 
### Developing PyFlink on Apple Silicon macOS

PyFlink version 1.15 has [known issues](https://issues.apache.org/jira/browse/FLINK-25188) on macOS machines using Apple Silicon chips (e.g. M1) due to Python dependencies.
You cannot install the `apache-pyflink==1.15.4` library on the machine, directly. This make it difficult to develop PyFlink 1.15 applications on Apple Silicon.

A workaround is running the Python interpreter in a Docker container built for Intel architecture, and running it in the M1 machine using the Rosetta emulation.

You can find the [step-by-step instructions to for PyFlink local development using docker on Apple Silicon machines](./LocalDevelopmentOnAppleSilicon).