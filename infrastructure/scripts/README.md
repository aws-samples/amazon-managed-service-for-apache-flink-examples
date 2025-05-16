## Useful shell script

This folder contains some useful shell script to interact with the Amazon Managed Service for Apache Flink API via AWS CLI.

All scripts have the following prerequisites:
* [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) 
* [jq](https://jqlang.org/) 

All scripts assume the default profile is authenticated to the AWS account hosting the Managed Flink application with sufficient 
permissions, and the default region matches the region of the application.
You can modify the script to pass AWS profile or region explicitly.

These scripts are for demonstration purposes only.

### Retrieve the status of the tasks

[`task_status.sh`](task_status.sh)

This script returns the status of each task in the Flink job.

This is useful for example to automate operation, to check whether an update has been successfully deployed or is stuck 
in a fail-and-restart loop due to some problem at runtime.

The job is up-and-running and processing data when all the tasks are `RUNNING`.

When the application is not `RUNNING`, the script always returns `UNKNOWN`

Example 1: the job has 3 tasks, is healthy and processing data

```shell
> ./task_status.sh MyApplication
RUNNING
RUNNING
RUNNING
```

Example 2: the job has 2 tasks, failing and restarting

```shell
> ./task_status.sh MyApplication
FAILED
CANCELED
```

Example 3: the application is not running

```shell
> ./task_status.sh MyApplication
UNKNOWN
```

