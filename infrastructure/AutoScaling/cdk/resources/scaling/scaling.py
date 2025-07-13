import boto3
import json
import os

client_kda = boto3.client('kinesisanalyticsv2')
client_ssm = boto3.client('ssm')
client_cloudwatch = boto3.client('cloudwatch')
client_cloudformation = boto3.client('cloudformation')
client_aas = boto3.client('application-autoscaling')
client_iam = boto3.resource('iam')

def update_parallelism(context, desiredCapacity, resourceName, appVersionId, currentParallelismPerKPU):
    try:
        # Compute the new parallelism based on the desired capacity and parallelismPerKPU
        newParallelism = desiredCapacity * currentParallelismPerKPU
        newParallelismPerKPU = currentParallelismPerKPU  # Assume no change for now

        # Call KDA service to update parallelism
        response = client_kda.update_application(
            ApplicationName=resourceName,
            CurrentApplicationVersionId=appVersionId,
            ApplicationConfigurationUpdate={
                'FlinkApplicationConfigurationUpdate': {
                    'ParallelismConfigurationUpdate': {
                        'ConfigurationTypeUpdate': 'CUSTOM',
                        'ParallelismUpdate': int(newParallelism),
                        'ParallelismPerKPUUpdate': int(newParallelismPerKPU),
                        'AutoScalingEnabledUpdate': False
                    }
                }
            }
        )

        print("In update_parallelism; response: ")
        print(response)
        scalingStatus = "InProgress"

    except Exception as e:
        print(e)
        scalingStatus = "Failed"

    return scalingStatus


def response_function(status_code, response_body):
    return_json = {
        'statusCode': status_code,
        'body': response_body,
        'headers': {
            'Content-Type': 'application/json',
        },
    }
    # log response
    print(return_json)
    return return_json


def handler(event, context):
    print(event)
    resourceName = os.environ['flinkApplicationName']
    scaleInFactor = int(os.environ['scaleInFactor'])
    scaleOutFactor = int(os.environ['scaleOutFactor'])
    scaleOperation = os.environ['scaleOperation']
    alarm_status = event['detail']['state']['value']
    alarm_name = event['detail']['alarmName']
    minKPU = int(os.environ['minKPU'])
    maxKPU = int(os.environ['maxKPU'])
    stop_scale = 0

    # get details for the KDA app in question
    appVersion = event["TaskResult"]["ApplicationDetail"]["ApplicationVersionId"]
    applicationStatus = event["TaskResult"]["ApplicationDetail"]["ApplicationStatus"]
    parallelism = event["TaskResult"]["ApplicationDetail"]["ApplicationConfigurationDescription"][
        "FlinkApplicationConfigurationDescription"]["ParallelismConfigurationDescription"]["Parallelism"]
    parallelismPerKPU = event["TaskResult"]["ApplicationDetail"]["ApplicationConfigurationDescription"]["FlinkApplicationConfigurationDescription"]["ParallelismConfigurationDescription"]["ParallelismPerKPU"]
    actualCapacity = (parallelism + parallelismPerKPU - 1) // parallelismPerKPU
    print(f"Actual Capacity: {actualCapacity}")

    if applicationStatus == "UPDATING":
        scalingStatus = "InProgress"
    elif applicationStatus == "RUNNING":
        scalingStatus = "Successful"

    # Scaling out scenario (ScaleOut)
    if "ScaleOut" in alarm_name and alarm_status == 'ALARM' and applicationStatus == 'RUNNING':
        if actualCapacity < maxKPU:
            if scaleOperation == 'Multiply/Divide':
                desiredCapacity = actualCapacity * scaleOutFactor
                print(f"Scaling out, desired capacity: {desiredCapacity}")
            else:
                desiredCapacity = actualCapacity + scaleOutFactor
                print(f"Scaling out, desired capacity: {desiredCapacity}")
            if desiredCapacity < maxKPU:
                update_parallelism(context, desiredCapacity, resourceName, appVersion, parallelismPerKPU)
            else:
                update_parallelism(context, maxKPU, resourceName, appVersion, parallelismPerKPU)
                print("Application will be set to Max KPU")
                stop_scale = 1
        else:
            desiredCapacity = actualCapacity
            print("Application is already equal or above to Max KPU")
            stop_scale = 1

    # Scaling in scenario (ScaleIn)
    elif "ScaleIn" in alarm_name and alarm_status == 'ALARM' and applicationStatus == 'RUNNING':
        if actualCapacity > minKPU:
            if scaleOperation == 'Multiply/Divide':
                desiredCapacity = int(actualCapacity / scaleInFactor)
                print(f"Scaling in, desired capacity: {desiredCapacity}")
            else:
                desiredCapacity = actualCapacity - scaleInFactor
                print(f"Scaling in, desired capacity: {desiredCapacity}")
            if desiredCapacity > minKPU:
                update_parallelism(context, desiredCapacity, resourceName, appVersion, parallelismPerKPU)
            else:
                update_parallelism(context, minKPU, resourceName, appVersion, parallelismPerKPU)
                print("Application will go below Min KPU")
                stop_scale = 1
        else:
            desiredCapacity = actualCapacity
            print("Application is already below or equal to Min KPU")
            stop_scale = 1

    else:
        desiredCapacity = actualCapacity
        print("Scaling still happening or not required")
        stop_scale = 0

    return response_function(200, stop_scale)
