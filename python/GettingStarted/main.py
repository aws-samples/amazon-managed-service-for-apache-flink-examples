'''
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy of this
software and associated documentation files (the "Software"), to deal in the Software
without restriction, including without limitation the rights to use, copy, modify,
merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
'''
# -*- coding: utf-8 -*-
"""
getting-started.py
~~~~~~~~~~~~~~~~~~~
This module:
    1. Creates a table environment
    2. Creates a source table from a Kinesis Data Stream
    3. Creates a sink table writing to a Kinesis Data Stream
    4. Inserts the source table data into the sink table
"""

from pyflink.table import EnvironmentSettings, TableEnvironment
import os
import json

# 1. Creates a Table Environment
env_settings = EnvironmentSettings.in_streaming_mode()
table_env = TableEnvironment.create(env_settings)

statement_set = table_env.create_statement_set()

AT_TIMESTAMP = "AT_TIMESTAMP"
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
        "file:///" + CURRENT_DIR + "/lib/pyflink-dependencies.jar",
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

def create_source_table(table_name, stream_name, region, stream_initpos, stream_initpos_timestamp):
    if stream_initpos == AT_TIMESTAMP and stream_initpos_timestamp == None:
            raise ValueError(f"A timestamp must be supplied for stream_initpos {AT_TIMESTAMP}")

    init_pos = "\n'scan.stream.initpos' = '{0}',".format(stream_initpos) if stream_initpos is not None else ''
    init_pos_timestamp = "\n'scan.stream.initpos-timestamp' = '{0}',".format(stream_initpos_timestamp) if stream_initpos_timestamp is not None else ''

    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price DOUBLE,
                event_time TIMESTAMP(3),
                WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND

              )
              PARTITIONED BY (ticker)
              WITH (
                'connector' = 'kinesis',
                'stream' = '{1}',
                'aws.region' = '{2}',{3}{4}
                'format' = 'json',
                'json.timestamp-format.standard' = 'ISO-8601'
              ) """.format(
        table_name, stream_name, region, init_pos, init_pos_timestamp
    )


def create_sink_table(table_name, stream_name, region):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price DOUBLE,
                event_time TIMESTAMP(3),
                WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
              )
              PARTITIONED BY (ticker)
              WITH (
                'connector' = 'kinesis',
                'stream' = '{1}',
                'aws.region' = '{2}',
                'sink.partitioner-field-delimiter' = ';',
                'sink.batch.max-size' = '100',
                'format' = 'json',
                'json.timestamp-format.standard' = 'ISO-8601'
              ) """.format(
        table_name, stream_name, region
    )


def create_print_table(table_name, stream_name, region, stream_initpos):
    return """ CREATE TABLE {0} (
                ticker VARCHAR(6),
                price DOUBLE,
                event_time TIMESTAMP(3),
                WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND

              )
              WITH (
                'connector' = 'print'
              ) """.format(
        table_name, stream_name, region, stream_initpos
    )

def main():
    # Application Property Keys
    input_property_group_key = "InputStream0"
    producer_property_group_key = "OutputStream0"

    input_stream_key = "stream.name"
    input_region_key = "aws.region"

    input_starting_position_key = "flink.stream.initpos"
    input_starting_position_timestamp = "flink.stream.initpos.timestamp"

    output_stream_key = "stream.name"
    output_region_key = "aws.region"

    # tables
    input_table_name = "input_table"
    output_table_name = "output_table"

    # get application properties
    props = get_application_properties()

    input_property_map = property_map(props, input_property_group_key)
    output_property_map = property_map(props, producer_property_group_key)

    input_stream = input_property_map[input_stream_key]
    input_region = input_property_map[input_region_key]

    stream_initpos = input_property_map[input_starting_position_key] if input_starting_position_key in input_property_map else None
    stream_initpos_timestamp = input_property_map[input_starting_position_timestamp] if input_starting_position_timestamp in input_property_map else None

    output_stream = output_property_map[output_stream_key]
    output_region = output_property_map[output_region_key]

    # 2. Creates a source table from a Kinesis Data Stream
    table_env.execute_sql(
        create_source_table(input_table_name, input_stream, input_region, stream_initpos, stream_initpos_timestamp)
    )

    # 3. Creates a sink table writing to a Kinesis Data Stream
    table_env.execute_sql(
        create_sink_table(output_table_name, output_stream, output_region)
    )

    # 4. Inserts the source table data into the sink table
    table_result = table_env.execute_sql("INSERT INTO {0} SELECT * FROM {1}"
                                         .format(output_table_name, input_table_name))

    if is_local:
        table_result.wait()


if __name__ == "__main__":
    main()