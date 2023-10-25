# This is a sample Python script.
import logging

from pyflink.common import Row
# Press ⌃R to execute it or replace it with your code.
# Press Double ⇧ to search everywhere for classes, files, tool windows, actions, and settings.

from pyflink.common.typeinfo import Types
from pyflink.datastream import StreamExecutionEnvironment, RuntimeContext, MapFunction
from pyflink.datastream.connectors import FlinkKafkaConsumer, FlinkKafkaProducer
from pyflink.common.serialization import JsonRowDeserializationSchema, SimpleStringSchema, JsonRowSerializationSchema
from pyflink.datastream.state import ValueStateDescriptor
import os
import json

APPLICATION_PROPERTIES_FILE_PATH = "/etc/flink/application_properties.json"  # on kda

env = StreamExecutionEnvironment.get_execution_environment()
is_local = (
    True if os.environ.get("IS_LOCAL") else False
)  # set this env var in your local environment

if is_local:
    # only for local, overwrite variable to properties and pass in your jars delimited by a semicolon (;)
    APPLICATION_PROPERTIES_FILE_PATH = "application_properties.json"  # local

    CURRENT_DIR = os.path.dirname(os.path.realpath(__file__))
    env.add_jars(
        f"file:///{CURRENT_DIR}/lib/flink-connector-kafka-1.15.2.jar",f"file:///{CURRENT_DIR}/lib/kafka-clients-3.3.1.jar"
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


class DeviceAggregation(MapFunction):

    def open(self, runtime_context: RuntimeContext):
        state_desc = ValueStateDescriptor('device_count', Types.INT())
        self.device_count = runtime_context.get_state(state_desc)

    def map(self, value):
        count = self.device_count.value()
        if count is None:
            count=0
        count = count+1
        self.device_count.update(count)
        return Row(value[0], count)


def demo_flink_json():
    input_topic_key = "input.topic"
    bootstrap_server_key = "bootstrap.servers"
    output_topic_key="output.topic"
    consumer_group_id="group.id"
    consumer_property_group_key = "consumer.config.0"
    producer_property_group_key = "producer.config.0"

    props = get_application_properties()
    consumer_property_map = property_map(props, consumer_property_group_key)
    producer_property_map  = property_map(props, producer_property_group_key)

    deserialization_schema = JsonRowDeserializationSchema.builder() \
        .type_info(type_info=Types.ROW_NAMED(["device_type","session_id"],[Types.STRING(), Types.STRING()])).ignore_parse_errors().build()

    serialization_schema = JsonRowSerializationSchema.builder().with_type_info(
        type_info=Types.ROW_NAMED(["device_type","total_count"],[Types.STRING(), Types.INT()])).build()
    kafka_consumer = FlinkKafkaConsumer(
        topics=consumer_property_map[input_topic_key],
        deserialization_schema=deserialization_schema,
        properties={'bootstrap.servers': consumer_property_map[bootstrap_server_key], 'group.id': consumer_property_map[consumer_group_id]}
    )
    kafka_consumer.set_start_from_latest()

    kafka_producer = FlinkKafkaProducer(
        topic=producer_property_map[output_topic_key],
        serialization_schema=serialization_schema,
        producer_config={'bootstrap.servers': producer_property_map[bootstrap_server_key]})

    ds = env.add_source(kafka_consumer)
    ds = ds.key_by(lambda x: x[0]).map(DeviceAggregation(), output_type=Types.ROW_NAMED(["device_type","total_count"],[Types.STRING(), Types.INT()]))

    ds.add_sink(kafka_producer)
    env.execute()



# Press the green button in the gutter to run the script.
if __name__ == '__main__':
    demo_flink_json()

# See PyCharm help at https://www.jetbrains.com/help/pycharm/
