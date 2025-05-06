#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import {KdaAutoscalingStack} from '../lib/kda-autoscaling-stack';
import {DefaultStackSynthesizer} from "aws-cdk-lib";

const app = new cdk.App();

const synthDate = new Date().toISOString().split('T')[0];

new KdaAutoscalingStack(app, 'KdaAutoscalingStack', {
    description: `MSF autoscaler (${synthDate})`,

    synthesizer: new DefaultStackSynthesizer({
        generateBootstrapVersionRule: false
    })
});


