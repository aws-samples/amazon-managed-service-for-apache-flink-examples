# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
# -*- coding: utf-8 -*-

"""
streaming-file-sink.py
~~~~~~~~~~~~~~~~~~~
This module:
    1. Creates a table environment
    2. Creates a synthetic source table
    3. Creates a sink table writing to an S3 Bucket
    4. Queries from the Source Table and inserts into the Sink table (S3)
"""

from pyflink.table import EnvironmentSettings, TableEnvironment, DataTypes
from pyflink.table.udf import udf
import os
import json

# 1. Creates a Table Environment
env_settings = EnvironmentSettings.in_streaming_mode()
table_env = TableEnvironment.create(env_settings)

APPLICATION_PROPERTIES_FILE_PATH = "/etc/flink/application_properties.json"  # on kda

is_local = (
    True if os.environ.get("IS_LOCAL") else False
)  # set this env var in your local environment

if is_local:
    # only for local, overwrite variable to properties and pass in your jars delimited by a semicolon (;)
    APPLICATION_PROPERTIES_FILE_PATH = "application_properties.json"  # local

    CURRENT_DIR = os.path.dirname(os.path.realpath(__file__))
    table_env.get_config().get_configuration().set_string(
        "pipeline.jars",
        "file:///" + CURRENT_DIR + "/lib/pyflink-dependencies.jar"
    )

    table_env.get_config().get_configuration().set_string(
        "execution.checkpointing.mode", "EXACTLY_ONCE"
    )

    table_env.get_config().get_configuration().set_string(
        "execution.checkpointing.interval", "1min"
    )


def get_application_properties():
    if os.path.isfile(APPLICATION_PROPERTIES_FILE_PATH):
        with open(APPLICATION_PROPERTIES_FILE_PATH, "r") as file:
            contents = file.read()
            properties = json.loads(contents)
            return properties
    else:
        print('A file at "{}" was not found'.format(APPLICATION_PROPERTIES_FILE_PATH))


def property_map(props, property_group_id):
    for prop in props:
        if prop["PropertyGroupId"] == property_group_id:
            return prop["PropertyMap"]


def create_source_table(table_name):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price DOUBLE,
                eventTime TIMESTAMP(3),
                WATERMARK FOR eventTime AS eventTime - INTERVAL '5' SECOND
              )
              WITH (
                  'connector' = 'datagen',
                  'fields.ticker.kind' = 'random',
                  'fields.ticker.length' = '6',
                  'fields.price.kind' = 'random',
                  'fields.price.min' = '10.0',
                  'fields.price.max' = '100.0',
                  'rows-per-second' = '5'
              ) """.format(table_name)


def create_sink_table(table_name, bucket_name):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price DOUBLE,
                eventTime BIGINT
              )
              WITH (
                  'connector'='filesystem',
                  'path'='s3a://{1}/output',
                  'format'='json',
                  'sink.partition-commit.policy.kind'='success-file',
                  'sink.partition-commit.delay' = '1 min'
              ) """.format(table_name, bucket_name)


@udf(input_types=[DataTypes.TIMESTAMP(3)], result_type=DataTypes.BIGINT())
def to_millis(timestamp):
    return int(timestamp.timestamp() * 1000)


table_env.create_temporary_system_function("to_millis", to_millis)


def main():
    # Application Property Keys
    s3_bucket_property_group_key = "bucket"
    s3_bucket_name_key = "name"

    # tables
    input_table_name = "input_table"
    output_table_name = "output_table"

    # get application properties
    props = get_application_properties()

    s3_bucket_property_map = property_map(props, s3_bucket_property_group_key)

    s3_bucket_name = s3_bucket_property_map[s3_bucket_name_key]

    # 2. Creates a synthetic source table
    create_source = create_source_table(input_table_name)
    table_env.execute_sql(create_source)

    # 3. Creates a sink table writing to an S3 Bucket
    create_sink = create_sink_table(output_table_name, s3_bucket_name)
    table_env.execute_sql(create_sink)

    # 4. Insert into the sink table (S3)
    table_result = table_env.execute_sql("""INSERT INTO {0} 
                                            SELECT ticker, price, to_millis(eventTime) as eventTime 
                                            FROM {1}""".format(output_table_name, input_table_name))

    if is_local:
        table_result.wait()


if __name__ == "__main__":
    main()
