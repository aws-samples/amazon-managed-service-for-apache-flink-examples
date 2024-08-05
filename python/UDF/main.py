"""
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
"""

"""
main.py
~~~~~~~~~~~~~~~~~~~
This module:
    1. Creates the execution environment
    2. Sets any special configuration for local mode (e.g. when running in the IDE)
    3. Defines and register a UDF
    4. Retrieves the runtime configuration
    5. Creates a source table to generate data using DataGen connector
    6. Creates a view from a query that uses the UDF
    7. Creates a sink table to Kinesis Data Streams and inserts into the sink table from the view
"""

from pyflink.table import EnvironmentSettings, TableEnvironment, DataTypes
from pyflink.table.udf import udf
import os
import json
import logging
import pyflink
import pathlib

#######################################
# 1. Creates the execution environment
#######################################

env_settings = EnvironmentSettings.in_streaming_mode()
table_env = TableEnvironment.create(env_settings)

python_source_dir = str(pathlib.Path(__file__).parent)

table_env.set_python_requirements(
    requirements_file_path="file:///" + python_source_dir + "/requirements.txt")


# Location of the configuration file when running on Managed Flink.
# NOTE: this is not the file included in the project, but a file generated by Managed Flink, based on the
# application configuration.
APPLICATION_PROPERTIES_FILE_PATH = "/etc/flink/application_properties.json"

# Set the environment variable IS_LOCAL=true in your local development environment,
# or in the run profile of your IDE: the application relies on this variable to run in local mode (as a standalone
# Python application, as opposed to running in a Flink cluster).
# Differently from Java Flink, PyFlink cannot automatically detect when running in local mode
is_local = (
    True if os.environ.get("IS_LOCAL") else False
)


##############################################
# 2. Set special configuration for local mode
##############################################

if is_local:
    # Load the configuration from the json file included in the project
    APPLICATION_PROPERTIES_FILE_PATH = "application_properties.json"

    # Point to the fat-jar generated by Maven, containing all jar dependencies (e.g. connectors)
    CURRENT_DIR = os.path.dirname(os.path.realpath(__file__))
    table_env.get_config().get_configuration().set_string(
        "pipeline.jars",
        # For local development (only): use the fat-jar containing all dependencies, generated by `mvn package`
        "file:///" + CURRENT_DIR + "/target/pyflink-dependencies.jar",
    )

    # Show the PyFlink home directory and the directory where logs will be written, when running locally
    print("PyFlink home: " + os.path.dirname(os.path.abspath(pyflink.__file__)))
    print("Logging directory: " + os.path.dirname(os.path.abspath(pyflink.__file__)) + '/log')

# Utility method, extracting properties from the runtime configuration file
def get_application_properties():
    if os.path.isfile(APPLICATION_PROPERTIES_FILE_PATH):
        with open(APPLICATION_PROPERTIES_FILE_PATH, "r") as file:
            contents = file.read()
            properties = json.loads(contents)
            return properties
    else:
        print('A file at "{}" was not found'.format(APPLICATION_PROPERTIES_FILE_PATH))


# Utility method, extracting a property from a property group
def property_map(props, property_group_id):
    for prop in props:
        if prop["PropertyGroupId"] == property_group_id:
            return prop["PropertyMap"]


##################################
# 3.  Defines and register a UDF
##################################

# Defines a scalar User Defined Function.
# In this simple example, it converts Celsius to Fahrenheit
@udf(input_types=[DataTypes.DOUBLE()], result_type=DataTypes.DOUBLE())
def celsius_to_fahrenheit(celsius):
    # The actual body of the UDF. It can be arbitrarily complicated.
    fahrenheit = celsius * 9 / 5 + 32

    # Logging here is for demonstration only.
    # Logging in a UDF is NOT recommended. Tt can have a substantial performance impact
    logging.debug("Celsius {} = Fahrenheit {}".format(celsius, fahrenheit))

    return fahrenheit


# Register the UDF
table_env.create_temporary_system_function("celsius_to_fahrenheit", celsius_to_fahrenheit)


@udf(input_types=[DataTypes.DOUBLE()], result_type=DataTypes.STRING())
def ask_bedrock_for_fun_fact(celsius):
    import boto3
    import botocore

    client = boto3.client("bedrock-runtime", region_name="us-east-1")
    model_id = "ai21.j2-mid-v1"

    user_message = "Give me a fun fact about the number " + str(celsius)
    conversation = [
        {
            "role": "user",
            "content": [{"text": user_message}],
        }
    ]

    try:
        # Send the message to the model, using a basic inference configuration.
        response = client.converse(
            modelId=model_id,
            messages=conversation,
            inferenceConfig={"maxTokens": 512, "temperature": 0.5, "topP": 0.9},
        )

        # Extract and print the response text.
        response_text = response["output"]["message"]["content"][0]["text"]
        return response_text

    except (botocore.exceptions.ClientError, Exception) as e:
        error_reason = "ERROR: Can't invoke " + {model_id} + ". Reason: " + {e}
        return error_reason


# Register the UDF
table_env.create_temporary_system_function("ask_bedrock_for_fun_fact", ask_bedrock_for_fun_fact)


def main():
    #####################################
    # 4. Retrieve runtime configuration
    #####################################

    props = get_application_properties()
    output_stream_name = property_map(props, "OutputStream0")["stream.name"]
    output_stream_region = property_map(props, "OutputStream0")["aws.region"]

    logging.info("Output stream: {}, region: {}".format(output_stream_name, output_stream_region))

    #################################################
    # 5. Define input table using datagen connector
    #################################################

    # In a real application, this table will probably be connected to a source stream, using for example the 'kinesis'
    # connector.
    table_env.execute_sql("""
            CREATE TABLE sensor_readings (
                sensor_id INT,
                temperature DOUBLE,
                measurement_time TIMESTAMP(3)
              )
              PARTITIONED BY (sensor_id)
              WITH (
                'connector' = 'datagen',
                'fields.sensor_id.min' = '10',
                'fields.sensor_id.max' = '20',
                'fields.temperature.min' = '0',
                'fields.temperature.max' = '100'
              )
    """)

    ###################################################
    # 6. Creates a view from a query that uses the UDF
    ###################################################

    table_env.execute_sql("""
            CREATE TEMPORARY VIEW sensor_readings_fahrenheit
            AS
            SELECT sensor_id, ask_bedrock_for_fun_fact(temperature) AS temperature_f, measurement_time
            FROM sensor_readings
    """)

    #################################################
    # 7. Define sink table using kinesis connector
    #################################################

    table_env.execute_sql(f"""
            CREATE TABLE output (
                sensor_id INT,
                temperature_f STRING,
                measurement_time TIMESTAMP(3)
              )
              PARTITIONED BY (sensor_id)
              WITH (
                'connector' = 'kinesis',
                'stream' = '{output_stream_name}',
                'aws.region' = '{output_stream_region}',
                'sink.partitioner-field-delimiter' = ';',
                'sink.batch.max-size' = '100',
                'format' = 'json',
                'json.timestamp-format.standard' = 'ISO-8601'
              )
        """)

    # For local development purposes, you might want to print the output to the console, instead of sending it to a
    # Kinesis Stream. To do that, you can replace the sink table using the 'kinesis' connector, above, with a sink table
    # using the 'print' connector. Comment the statement immediately above and uncomment the one immediately below.

    # table_env.execute_sql("""
    #     CREATE TABLE output (
    #             sensor_id INT,
    #             temperature_f STRING,
    #             measurement_time TIMESTAMP(3)
    #           )
    #           WITH (
    #             'connector' = 'print'
    #           )
    # """)

    # Executing an INSERT INTO statement will trigger the job
    table_result = table_env.execute_sql("""
            INSERT INTO output
            SELECT sensor_id, temperature_f, measurement_time
                FROM sensor_readings_fahrenheit
    """)

    # When running locally, as a standalone Python application, you must instruct Python not to exit at the end of the
    # main() method, otherwise the job will stop immediately.
    # When running the job deployed in a Flink cluster or in Amazon Managed Service for Apache Flink, the main() method
    # must end once the flow has been defined and handed over to the Flink framework to run.
    if is_local:
        table_result.wait()


if __name__ == "__main__":
    main()
