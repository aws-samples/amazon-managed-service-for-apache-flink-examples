## Running examples locally, in IntelliJ

Most of the examples in this directory are designed to also run locally, withing IntelliJ, without code changes.

You do not need to run a local Flink cluster or installing Flink on your development machine.

As long as you have access to the external resources, you can run your application directly in IntelliJ, like any other Java application.

### Prerequisites to run the application in IntelliJ

#### Set up the local runtime configuration

Most of the examples are designed to detect when running locally, and read the runtime properties from the
`flink-application-properties-dev.json` located in the resource folder of the project.

Before running the application, edit the `flink-application-properties-dev.json` update the file to match the actual 
resource names and locations (e.g. bucket name, stream name) used by your application.

#### Include Flink *provided* dependencies

All dependencies defined in the POM with `provided` scope must be included in the classpath when running the applicatin locally.

1. Create a *Run Configuration* for the main class of your application
2. Edit *Run Configuration*, click *Modify options* and select *Add dependencies with "provided" scope to the classpath*

#### IAM permissions

Create an AWS profile on your machine with sufficient permissions to access all the AWS services the application
is using.

Use the [AWS toolkit](https://aws.amazon.com/intellij/) IntelliJ plugin to run the application using that profile:

1. In the AWS plugin, select the profile and the region
2. Create a *Run Configuration* for the main class of your application
3. Edit *Run Configuration*:  under *AWS configuration*, select *Use the currently selected credential profile/region*.


#### Network access to VPC-networked services

Note that, if your application connects to any AWS service using VPC networking, like MSK or RDS, you need to set up connectivity
between your developer machine and the VPC hosting the service, for example using a VPN.

If this is not possible, you should consider running a local version of the service, installed directly or your machine 
in a container.
