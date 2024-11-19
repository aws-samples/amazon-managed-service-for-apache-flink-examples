# Contributing Guidelines

Thank you for your interest in contributing to our project. Whether it's a bug report, new feature, correction, or additional
documentation, we greatly value feedback and contributions from our community.

Please read through this document before submitting any issues or pull requests to ensure we have all the necessary
information to effectively respond to your bug report or contribution.

## Goal of this repository

The goal of this repository is to provide working example of common patterns for Apache Flink in general, and, in 
particular, for [Amazon Managed Service for Apache Flink](https://aws.amazon.com/managed-service-apache-flink).
Each example illustrates a single, specific pattern, trying to make it simple removing external dependencies when possible.

The examples also try to illustrate best practices and consistent approaches to common problems.

The goal of this repository is not to illustrate *solutions*, end-to-end architectures.

The AWS team managing the repository reserves the right to modify or reject new example proposals.

### Guidelines or new examples

* An example should focus on a single Flink API, unless the goal is to show the integration between different APIs.
* Unless the goal of the example is showing a particular sink, the application should sink data to Kinesis Data Stream,
    as JSON. This makes the output more easily verifiable, simplify the setup when running locally, and testing the application.
* Examples should all work both locally, in the IDE (e.g. IntelliJ, PyCharm), and on Managed Service for Apache Flink,
  without any code changes.

#### Runtime configuration

* follow the pattern used in
  [this example](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/tree/main/java/GettingStarted)
  to provide the configuration when running locally.
* Use separate `PropertyGroupID` for each component, for example for each source and sink. Name them groups and  
  properties consistently. See at
  [this configuration](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/blob/main/java/Windowing/src/main/resources/flink-application-properties-dev.json)
  as an example.
  * The committed `flink-application-properties-dev.json` file MUST NOT contain any real resource ARN, Account ID, URL, 
  or any secrets. Use placeholders instead (e.g. `arn:aws:kinesis:<region>:<accountId>:stream/OutputStream`) or obviously
  fake resource names. 
 
#### External resources

* Minimize the external resources.
* If the application requires external resources, provide instructions in the README.
* Providing an additional CloudFormation template is optional and does not exempt from human-readable instructions in 
  the README.

#### External data generators

* Avoid depending on external data generators as possible. Use the [DataGen connector](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/)
  to generate synthetic data.
* If the example requires an external data generator, try to use [this one](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/tree/main/python/data-generator), 
  or include it in the example. 

#### README and documentation

* Write an exhaustive README explaining what is the goal of the example, how the application works, Flink and connector
  versions, external dependencies, permissions, and runtime configuration. Use [this example](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/tree/main/java/KafkaConfigProviders/Kafka-SASL_SSL-ConfigProviders)
  as a reference.
* Make sure the example works with what explained in the README, and without any implicit dependency or configuration.

#### AWS authentication and credentials

* AWS credentials must never be explicitly passed to the application. 
* Any permissions must be provided from the IAM Role assigned to the Managed Apache Flink application. When running locally, leverage the IDE AWS plugins.

#### Dependencies in PyFlink examples
  * Use the pattern illustrated by [this example](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/tree/main/python/GettingStarted)
    to provide JAR dependencies and build the ZIP using Maven.
  * If the application also requires Python dependencies, use the pattern illustrated by [this example](https://github.com/aws-samples/amazon-managed-service-for-apache-flink-examples/tree/main/python/PythonDependencies)
    leveraging `requirements.txt`.

## Reporting Bugs/Feature Requests

We welcome you to use the GitHub issue tracker to report bugs or suggest features.

When filing an issue, please check existing open, or recently closed, issues to make sure somebody else hasn't already
reported the issue. Please try to include as much information as you can. Details like these are incredibly useful:

* A reproducible test case or series of steps
* The version of our code being used
* Any modifications you've made relevant to the bug
* Anything unusual about your environment or deployment


## Contributing via Pull Requests

Contributions via pull requests are much appreciated. Before sending us a pull request, please ensure that:

1. You are working against the latest source on the *main* branch.
2. You check existing open, and recently merged, pull requests to make sure someone else hasn't addressed the problem already.
3. You open an issue to discuss any significant work - we would hate for your time to be wasted.

To send us a pull request, please:

1. Fork the repository.
2. Modify the source; please focus on the specific change you are contributing. If you also reformat all the code, it will be hard for us to focus on your change.
3. Update the README to reflect any changes in the code.
4. Ensure any local tests pass.
5. **Manually test** your changes running the application locally, in the IDE, AND in Managed Service for Apache Flink. The application must run in both without code changes.
6. Commit to your fork using clear commit messages. Ensure you do not commit any private configuration. In particular, check the local configuration JSON file.
7. Send us a pull request, describing your changes.
8. Pay attention to any automated CI failures reported in the pull request, and stay involved in the conversation.

Refer to GitHub documentation about [forking a repository](https://help.github.com/articles/fork-a-repo/) and [creating a pull request](https://help.github.com/articles/creating-a-pull-request/), for further details.


## Code of Conduct

This project has adopted the [Amazon Open Source Code of Conduct](https://aws.github.io/code-of-conduct).
For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq) or contact
opensource-codeofconduct@amazon.com with any additional questions or comments.


## Security issue notifications
If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public github issue.


## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.
