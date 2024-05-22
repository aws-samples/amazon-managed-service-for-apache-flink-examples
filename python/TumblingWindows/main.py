# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: MIT-0
# -*- coding: utf-8 -*-

"""
main.py
~~~~~~~~~~~~~~~~~~~
This module:
    1. Creates a table environment
    2. Creates a source table from a Kinesis Data Stream
    3. Creates a sink table writing to a Kinesis Data Stream
    4. Queries from the Source Table and
       creates a tumbling window over 10 seconds to calculate the cumulative price over the window.
    5. These tumbling window results are inserted into the Sink table.
"""

from pyflink.table import EnvironmentSettings, TableEnvironment, DataTypes
from pyflink.table.window import Tumble
from pyflink.table.expressions import col, lit
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
        # "file:///" + CURRENT_DIR + "/lib/flink-sql-connector-kinesis-4.2.0-1.18.jar",
        "file:///" + CURRENT_DIR + "/target/pyflink-dependencies.jar",
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


def create_input_table(table_name):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price NUMERIC(6,2),
                event_time TIMESTAMP(3),
                WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
              )
              PARTITIONED BY (ticker)
              WITH (
                'connector' = 'datagen',
                'fields.ticker.length' = '3',
                'fields.price.min' = '50',
                'fields.price.max' = '200'
              ) """.format(table_name)

def create_output_table(table_name, stream_name, region):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price NUMERIC(6,2),
                event_time VARCHAR(64)
              )
              PARTITIONED BY (ticker)
              WITH (
                'connector' = 'kinesis',
                'stream' = '{1}',
                'aws.region' = '{2}',
                'format' = 'json',
                'json.timestamp-format.standard' = 'ISO-8601'
              ) """.format(table_name, stream_name, region)


def perform_tumbling_window_aggregation(input_table_name):
    # use SQL Table in the Table API
    input_table = table_env.from_path(input_table_name)

    tumbling_window_table = (
        input_table.window(
            Tumble.over(lit(10).seconds).on(col("event_time")).alias("ten_second_window")
        )
        .group_by(col('ticker'), col('price'), col('ten_second_window'))
        .select(col('ticker'), col('price').min.alias('price'), (to_string(col('ten_second_window').end)).alias('event_time'))
    )

    return tumbling_window_table


@udf(input_types=[DataTypes.TIMESTAMP(3)], result_type=DataTypes.STRING())
def to_string(i):
    return str(i)


table_env.create_temporary_system_function("to_string", to_string)

def main():
    # Application Property Keys
    producer_property_group_key = "OutputStream0"

    output_stream_key = "stream.name"
    output_region_key = "aws.region"

    # tables
    input_table_name = "input_table"
    output_table_name = "output_table"

    # get application properties
    props = get_application_properties()

    output_property_map = property_map(props, producer_property_group_key)

    output_stream = output_property_map[output_stream_key]
    output_region = output_property_map[output_region_key]

    # 2. Creates a source table from a Kinesis Data Stream
    table_env.execute_sql(create_input_table(input_table_name))

    # 3. Creates a sink table writing to a Kinesis Data Stream
    table_env.execute_sql(create_output_table(output_table_name, output_stream, output_region))

    # 4. Queries from the Source Table and creates a tumbling window over 10 seconds to calculate the cumulative PRICE
    # over the window.
    tumbling_window_table = perform_tumbling_window_aggregation(input_table_name)
    table_env.create_temporary_view("tumbling_window_table", tumbling_window_table)

    # 5. These tumbling windows are inserted into the sink table
    table_result = table_env.execute_sql("INSERT INTO {0} SELECT * FROM {1}"
                                         .format(output_table_name, "tumbling_window_table"))

    if is_local:
        table_result.wait()

if __name__ == "__main__":
    main()
