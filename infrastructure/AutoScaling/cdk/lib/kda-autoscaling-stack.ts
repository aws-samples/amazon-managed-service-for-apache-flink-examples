import * as cdk from 'aws-cdk-lib';
import {Aws, aws_events_targets, CfnCondition, CfnJson, CfnParameter, Fn, Token} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as events from 'aws-cdk-lib/aws-events';
import * as sfn from 'aws-cdk-lib/aws-stepfunctions';
import * as tasks from 'aws-cdk-lib/aws-stepfunctions-tasks';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import {readFileSync} from 'fs';

function removePrefix(str: string, prefix: string): string {
    const regex = new RegExp(`^${prefix}`);
    return str.replace(regex, '');
}

enum MetricStats {
    Maximum = 'Maximum',
    Minimum = 'Minimum',
    Average = 'Average',
    Sum = 'Sum',
}

enum MetricType {
    KinesisAnalytics = "KinesisAnalytics",
    MSK = "MSK",
    Kinesis = "Kinesis",
    KinesisEFO = "KinesisEFO"
}


export class KdaAutoscalingStack extends cdk.Stack {
    constructor(scope: Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);

        /// CDK CLI parameters (at synth)

        const metricTypeStr: string = this.node.tryGetContext("type") || "KinesisAnalytics";
        if (!Object.values(MetricType).includes(metricTypeStr as MetricType)) {
            console.error(`Invalid metric type: "${metricTypeStr}". Expected one of ${Object.values(MetricType).join(", ")}`);
            process.exit(1);
        }
        const metricType: MetricType = metricTypeStr as MetricType;

        const autoscaleMetricName_: string | undefined = this.node.tryGetContext("metric");
        const autoscaleMetricStat_: string = this.node.tryGetContext("stat") || "Maximum";
        const triggerMetricPeriod_: number = Number(this.node.tryGetContext('period') ?? 60);

        /// Validate CDK parameters

        if (autoscaleMetricName_ === undefined) {
            console.error("Metric name not defined")
            process.exit(1)
        }


        if (!(autoscaleMetricStat_ in MetricStats)) {
            console.error(`Invalid metric stat. Must be one of ${Object.values(MetricStats).join(", ")}`)
            process.exit(1)
        }


        /// Base CFN parameters, always present

        const flinkApplicationName = new CfnParameter(this, 'ApplicationName', {
            type: "String",
            description: 'The name of the Amazon Managed Apache Flink Application to control'
        });

        const maxKPU = new CfnParameter(this, 'MaxKPU', {
            type: "Number",
            description: 'Maximum number of KPUs the Managed Flink Application may scale-out to',
            default: '10'
        });

        const minKPU = new CfnParameter(this, 'MinKPU', {
            type: "Number",
            description: 'Minimum number of KPUs the Managed Flink Application may scale-in to',
            default: '1'
        });

        const scaleMetricPeriod = new CfnParameter(this, 'MetricPeriod', {
            type: "Number",
            description: "Duration of each individual data point for an alarm (Seconds)",
            default: 60,
            allowedValues: ["1", "5", "10", "30", "60", "120", "180", "240", "300"],
        });

        const scaleInMetricThreshold = new CfnParameter(this, 'ScaleInThreshold', {
            type: "Number",
            description: "Lower threshold for the metric to trigger the scaling-in alarm",
            default: 20
        });

        const scaleOutMetricThreshold = new CfnParameter(this, 'ScaleOutThreshold', {
            type: "Number",
            description: "Upper threshold for the metric to trigger the scaling-out alarm",
            default: 80
        });

        const autoscaleEvaluationPeriod = new CfnParameter(this, 'ScalingEvaluationPeriod', {
            type: "Number",
            description: "Number of datapoints (metric periods) considered to evaluate the scaling alarm",
            default: 5
        });

        const autoscaleDataPointsAlarm = new CfnParameter(this, 'DataPointsToTriggerAlarm', {
            type: "Number",
            description: "Number of datapoints (metric periods) beyond threshold that trigger the scaling alarm",
            default: 5
        });

        const autoscaleCoolingDownPeriod = new CfnParameter(this, 'ScalingCoolingDownPeriod', {
            type: "Number",
            description: "Cool-down time, after the application has scaled in or out, before reconsidering the scaling alarm (in seconds)",
            default: 300
        });

        const updateWaitingTime = new CfnParameter(this, 'UpdateApplicationWaitingTime', {
            type: "Number",
            description: "Time given to the application to complete updating, in seconds",
            default: 60
        });

        const scaleInFactor = new CfnParameter(this, 'ScaleInFactor', {
            type: "Number",
            description: "Factor subtracted to (Subtract) or divided by (Divide) the current parallelism, on a scale-in event",
            default: 2
        });

        const scaleOutFactor = new CfnParameter(this, 'ScaleOutFactor', {
            type: "Number",
            description: "Factor added to (Add) or multiplied by (Multiply) the current parallelism, on a scale-out event",
            default: 2
        });

        const scaleOperation = new CfnParameter(this, "ScaleOperation", {
            type: "String",
            description: "Operation to calculate the new parallelism (Multiply/Divide or Add/Subtract) when scaling out/in",
            allowedValues: ["Multiply/Divide", "Add/Subtract"]
        });

        // Define CFN template interface with the base parameter, always present
        this.templateOptions.metadata = {
            'AWS::CloudFormation::Interface': {
                ParameterGroups: [
                    {
                        Label: {default: `Amazon Managed Flink application scaling (metric type: ${metricType}, metric: ${autoscaleMetricName_}, stat: ${autoscaleMetricStat_})`},
                        Parameters: [flinkApplicationName.logicalId, maxKPU.logicalId, minKPU.logicalId]
                    },
                    {
                        Label: {default: 'CloudWatch Scaling Alarm'},
                        Parameters: [scaleMetricPeriod.logicalId, autoscaleEvaluationPeriod.logicalId, autoscaleDataPointsAlarm.logicalId]
                    },
                    {
                        Label: {default: 'Autoscaling cycle'},
                        Parameters: [autoscaleCoolingDownPeriod.logicalId, updateWaitingTime.logicalId]
                    },
                    {
                        Label: {default: 'Scaling parameters'},
                        Parameters: [scaleOperation.logicalId, scaleOutFactor.logicalId, scaleOutMetricThreshold.logicalId, scaleInFactor.logicalId, scaleInMetricThreshold.logicalId]
                    },
                ],
                ParameterLabels: {
                    ApplicationName: {default: 'Application Name'},
                    MaxKPU: {default: 'Maximum KPU'},
                    MinKPU: {default: 'Minimum KPU'},
                    MetricPeriod: {default: 'Metric data point duration (sec)'},
                    ScalingEvaluationPeriod: {default: 'Scaling alarm evaluation datapoints'},
                    DataPointsToTriggerAlarm: {default: 'Number of datapoints beyond threshold to trigger the alarm'},
                    ScalingCoolingDownPeriod: {default: 'Cooling down period (grace period) after scaling (sec)'},
                    UpdateApplicationWaitingTime: {default: 'Waiting time while updating (sec)'},
                    ScaleInFactor: {default: 'Scale-in factor'},
                    ScaleOutFactor: {default: 'Scale-out factor'},
                    ScaleOperation: {default: 'Scale operation (mandatory)'},
                    ScaleInThreshold: {default: 'Scale-in metric threshold'},
                    ScaleOutThreshold: {default: 'Scale-out metric threshold'},
                }
            }
        }

        const applicationName = flinkApplicationName.valueAsString.trim();
        let triggerMetricNamespace;
        let kinesisDataStreamName: cdk.CfnParameter
        let kinesisConsumerName: cdk.CfnParameter
        let triggerMetricDimensions: cdk.aws_cloudwatch.DimensionsMap;

        // Conditionally add additional CFN parameters and labels, and generate the metric parameters, depending on the metric type
        switch (metricType) {

            case MetricType.KinesisAnalytics:
                // Metric parameters
                triggerMetricNamespace = "AWS/KinesisAnalytics";
                triggerMetricDimensions = {
                    Application: applicationName
                }

                break;

            case MetricType.Kinesis:
                /// Conditionally add KinesisStreamName CFN Parameter

                // CFN parameters
                kinesisDataStreamName = new CfnParameter(this, "KinesisDataStreamName", {
                    type: "String",
                    description: "Name of the Kinesis Data Stream"
                });


                // CFN parameter group
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterGroups.push({
                    Label: {default: 'Kinesis metrics'},
                    Parameters: [kinesisDataStreamName.logicalId]
                });

                // CFN parameter labels
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.KinesisDataStreamName = {
                    default: 'Kinesis Data Stream name'
                };

                // Metric parameters
                triggerMetricNamespace = "AWS/Kinesis";
                triggerMetricDimensions = {
                    StreamName: kinesisDataStreamName.valueAsString.trim()
                }
                break;

            case MetricType.KinesisEFO:
                /// Conditionally add KinesisStreamName and ConsumerName CFN Parameters

                // CFN parameters
                kinesisDataStreamName = new CfnParameter(this, "KinesisDataStreamName", {
                    type: "String",
                    description: "Name of the Kinesis Data Stream"
                });
                kinesisConsumerName = new CfnParameter(this, "KinesisConsumerName", {
                    type: "String",
                    description: "EFO Consumer Name"
                });

                // CFN parameter group
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterGroups.push({
                    Label: {default: 'Kinesis EFO consumer metrics'},
                    Parameters: [kinesisDataStreamName.logicalId, kinesisConsumerName.logicalId]
                });

                // CFN parameter labels
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.KinesisDataStreamName = {
                    default: 'Kinesis Data Stream name'
                };
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.KinesisConsumerName = {
                    default: 'Kinesis EFO Consumer name (optional)'
                };


                // Kinesis EFO consumer metric parameters
                triggerMetricNamespace = "AWS/Kinesis";
                triggerMetricDimensions = {
                    StreamName: kinesisDataStreamName.valueAsString.trim(),
                    ConsumerName: kinesisConsumerName.valueAsString.trim()
                }
                break;

            case MetricType.MSK:
                // Conditionally add CFN parameters and labels for MSK Consumer Group metrics
                const mskClusterName = new CfnParameter(this, "MSKClusterName", {
                    type: "String",
                    description: "If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the name of the MSK Cluster for monitoring"
                });
                const kafkaTopicName = new CfnParameter(this, "KafkaTopicName", {
                    type: "String",
                    description: "If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the Kafka Topic for monitoring"
                });
                const kafkaConsumerGroup = new CfnParameter(this, "KafkaConsumerGroupName", {
                    type: "String",
                    description: "If you choose topic metrics (MaxOffsetLag, SumOffsetLag or EstimatedMaxTimeLag) as metric to scale, you need to provide the Consumer Group name for monitoring"
                });

                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterGroups.push({
                    Label: {default: 'MSK Consumer Group metrics'},
                    Parameters: [mskClusterName.logicalId, kafkaTopicName.logicalId, kafkaConsumerGroup.logicalId,]
                });

                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.MSKClusterName = {
                    default: 'MSK cluster name'
                };
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.KafkaConsumerGroupName = {
                    default: 'Kafka consumer group name'
                };
                this.templateOptions.metadata['AWS::CloudFormation::Interface'].ParameterLabels.KafkaTopicName = {
                    default: 'Kafka topic name'
                };

                // MSK Consumer Group metric parameters
                triggerMetricNamespace = "AWS/Kafka";
                triggerMetricDimensions = {
                    "Cluster Name": mskClusterName.valueAsString,
                    "Consumer Group": kafkaConsumerGroup.valueAsString,
                    "Topic": kafkaTopicName.valueAsString
                }
                break;

            default:
                throw new Error("Invalid metric type: " + metricType);

        }


        /// Metric
        const triggerMetric: cdk.aws_cloudwatch.Metric = new cloudwatch.Metric({
                namespace: triggerMetricNamespace,
                metricName: autoscaleMetricName_,
                statistic: autoscaleMetricStat_,
                period: cdk.Duration.seconds(triggerMetricPeriod_),
                dimensionsMap: triggerMetricDimensions
            }
        );


        /// Alarms
        const scaleOutAlarm: cdk.aws_cloudwatch.Alarm = new cloudwatch.Alarm(this, `Scale-out alarm on ${autoscaleMetricName_}`, {
            comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD,
            alarmName: `ScaleOutAlarm-${applicationName}`,
            threshold: scaleOutMetricThreshold.valueAsNumber,
            evaluationPeriods: autoscaleEvaluationPeriod.valueAsNumber,
            datapointsToAlarm: autoscaleDataPointsAlarm.valueAsNumber,
            metric: triggerMetric
        });
        const scaleInAlarm: cdk.aws_cloudwatch.Alarm = new cloudwatch.Alarm(this, `Scale-in alarm on ${autoscaleMetricName_}`, {
            comparisonOperator: cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD,
            alarmName: `ScaleInAlarm-${applicationName}`,
            threshold: scaleInMetricThreshold.valueAsNumber,
            evaluationPeriods: autoscaleEvaluationPeriod.valueAsNumber,
            datapointsToAlarm: autoscaleDataPointsAlarm.valueAsNumber,
            metric: triggerMetric
        });


        /// Rule
        const scaleRule: cdk.aws_events.Rule = new events.Rule(this, `Rule on ${autoscaleMetricName_}`, {
            ruleName: `ScalingRule-${applicationName}`,
            eventPattern: {
                source: ["aws.cloudwatch"],
                detailType: ["CloudWatch Alarm State Change"],
                resources: [scaleOutAlarm.alarmArn, scaleInAlarm.alarmArn],
                detail: {
                    state: {
                        "value": ["ALARM"]
                    }
                }
            },
        });


        /// Lambda

        // Allow Lambda to log to CW
        const accessCWLogsPolicy: cdk.aws_iam.PolicyDocument = new iam.PolicyDocument({
            statements: [
                new iam.PolicyStatement({
                    resources: [`arn:aws:logs:${this.region}:${this.account}:log-group:/aws/lambda/*`],
                    actions: ['logs:CreateLogGroup', 'logs:CreateLogStream', 'logs:PutLogEvents'],
                }),
            ],
        });

        // Allow Lambda to describe and update kinesisanalytics application
        const kdaAccessPolicy: cdk.aws_iam.PolicyDocument = new iam.PolicyDocument({
            statements: [
                new iam.PolicyStatement({
                    resources: [`arn:aws:kinesisanalytics:${this.region}:${this.account}:application/${applicationName}`],
                    actions: ['kinesisanalytics:DescribeApplication', 'kinesisAnalytics:UpdateApplication']
                }),
            ],
        });

        // Lambda IAM role
        const lambdaRole: cdk.aws_iam.Role = new iam.Role(this, 'Scaling Function Role', {
            assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
            description: 'Lambda Scaling Role',
            inlinePolicies: {
                AccessCWLogsPolicy: accessCWLogsPolicy,
                AccessKDA: kdaAccessPolicy,
            },
        });

        // Create Lambda function
        const lambdaCode = readFileSync("resources/scaling/scaling.py", "utf-8")
        const lambdaFunction: cdk.aws_lambda.Function = new lambda.Function(this, 'Scaling Function', {
            runtime: lambda.Runtime.PYTHON_3_9,
            handler: "index.handler",
            role: lambdaRole,
            code: lambda.Code.fromInline(lambdaCode),
            environment: {
                flinkApplicationName: applicationName,
                scaleInFactor: scaleInFactor.valueAsString,
                scaleOutFactor: scaleOutFactor.valueAsString,
                scaleOperation: scaleOperation.valueAsString,
                maxKPU: maxKPU.valueAsString,
                minKPU: minKPU.valueAsString
            }
        })


        /// Step Functions

        // IAM Policy to be added to StepFunction to allow KinesisAnalytics DescribeApplication.
        // Note that CallAwsService is supposed to add the correct IAM permissions automatically, based on `service`,
        // `action`, and `iamResources`. However, because the service API is "kinesisanalyticsv2" while the IAM action
        // prefix is "kinesisanalytics" this does not work properly
        const kdaDescribeApplicationPolicyStatement: cdk.aws_iam.PolicyStatement = new iam.PolicyStatement({
            resources: [`arn:aws:kinesisanalytics:${this.region}:${this.account}:application/${applicationName}`],
            actions: ['kinesisanalytics:DescribeApplication']
        });

        const describeKdaApplicationTask: cdk.aws_stepfunctions_tasks.CallAwsService = new tasks.CallAwsService(this, "Describe Application", {
            service: 'kinesisanalyticsv2', // API is "v2"
            action: 'describeApplication',
            iamResources: [`arn:aws:kinesisanalytics:${this.region}:${this.account}:application/${applicationName}`],
            additionalIamStatements: [kdaDescribeApplicationPolicyStatement], // ensure IAM policy has the correct permissions
            parameters: {
                "ApplicationName": applicationName
            },
            resultPath: "$.TaskResult"
        })

        const scalingChoice: cdk.aws_stepfunctions.Choice = new sfn.Choice(this, 'Still updating?');
        const lambdaChoice: cdk.aws_stepfunctions.Choice = new sfn.Choice(this, 'MinMax KPU Reached?');
        const alarmChoice: cdk.aws_stepfunctions.Choice = new sfn.Choice(this, 'Still in alarm?');

        const waitUpdate: cdk.aws_stepfunctions.Wait = new sfn.Wait(this, "Waiting for application to finish updating", {
            time: sfn.WaitTime.duration(cdk.Duration.seconds(updateWaitingTime.valueAsNumber))
        });
        const waitCoolingPeriod: cdk.aws_stepfunctions.Wait = new sfn.Wait(this, "Cooling Period for Alarm", {
            time: sfn.WaitTime.duration(cdk.Duration.seconds(autoscaleCoolingDownPeriod.valueAsNumber))
        });

        // Add conditions with .when()
        const successState: cdk.aws_stepfunctions.Pass = new sfn.Pass(this, 'SuccessState');

        const lambdaTask: cdk.aws_stepfunctions_tasks.LambdaInvoke = new tasks.LambdaInvoke(this, 'Invoke Lambda Function', {
            lambdaFunction: lambdaFunction,
            resultPath: "$.TaskResult"
        })

        const describeKdaApplicationAfterScalingTask: cdk.aws_stepfunctions_tasks.CallAwsService = new tasks.CallAwsService(this, "Describe Application after scaling", {
            service: 'kinesisanalyticsv2', // API is "v2"
            action: 'describeApplication',
            iamResources: [`arn:aws:kinesisanalytics:${this.region}:${this.account}:application/${applicationName}`],
            additionalIamStatements: [kdaDescribeApplicationPolicyStatement], // ensure IAM policy has the correct permissions
            parameters: {
                "ApplicationName": applicationName
            },
            resultPath: "$.TaskResult",
        })


        const describeCwAlarmTask: cdk.aws_stepfunctions_tasks.CallAwsService = new tasks.CallAwsService(this, "Describe Alarm after waiting", {
            service: 'cloudwatch',
            action: 'describeAlarms',
            iamResources: [`arn:aws:cloudwatch:${this.region}:${this.account}:alarm:*`],
            parameters: {
                "AlarmNames.$": "States.Array($.detail.alarmName)",
                "AlarmTypes": ['CompositeAlarm', 'MetricAlarm']
            },
            resultPath: "$.TaskResult"
        })

        const definition: sfn.Chain = describeKdaApplicationTask
            .next(lambdaTask)
            .next(lambdaChoice
                .when(sfn.Condition.numberEquals('$.TaskResult.Payload.body', 1), successState)
                .otherwise(waitUpdate
                    .next(describeKdaApplicationAfterScalingTask)
                    .next(scalingChoice
                        .when(sfn.Condition.stringEquals('$.TaskResult.ApplicationDetail.ApplicationStatus', 'UPDATING'), waitUpdate)
                        .otherwise(waitCoolingPeriod
                            .next(describeCwAlarmTask.next(alarmChoice
                                .when(sfn.Condition.stringEquals('$.TaskResult.MetricAlarms[0].StateValue', 'ALARM'), describeKdaApplicationTask)
                                .otherwise(successState)))))));

        const stateMachine: sfn.StateMachine = new sfn.StateMachine(this, 'StateMachine', {
            definitionBody: sfn.DefinitionBody.fromChainable(definition)
        });

        const stepFunctionPolicy: cdk.aws_iam.PolicyDocument = new iam.PolicyDocument({
            statements: [
                new iam.PolicyStatement({
                    resources: [stateMachine.stateMachineArn],
                    actions: ['states:StartExecution']
                }),
            ],
        });

        const eventBridgeRole: cdk.aws_iam.Role = new iam.Role(this, 'EventBridge Role', {
            assumedBy: new iam.ServicePrincipal('events.amazonaws.com'),
            description: 'EventBridge Role',
            inlinePolicies: {
                AccessStepFunctionsPolicy: stepFunctionPolicy
            },
        });

        scaleRule.addTarget(new aws_events_targets.SfnStateMachine(stateMachine, {
            role: eventBridgeRole
        }));


        /// CFN Outputs
        new cdk.CfnOutput(this, "Autoscaler Metric Name", {value: autoscaleMetricName_});
        new cdk.CfnOutput(this, "Autoscaler Metric Statistic", {value: autoscaleMetricStat_});
        new cdk.CfnOutput(this, "Application name", {value: applicationName});
        new cdk.CfnOutput(this, "CW Metric Namespace", {value: triggerMetricNamespace});
        new cdk.CfnOutput(this, "CW Metric Dimensions", {value: JSON.stringify(triggerMetricDimensions)});

        new cdk.CfnOutput(this, "Scale-in alarm name", {value: scaleInAlarm.alarmName});
        new cdk.CfnOutput(this, "Scale-out alarm name", {value: scaleOutAlarm.alarmName});

    }
}

