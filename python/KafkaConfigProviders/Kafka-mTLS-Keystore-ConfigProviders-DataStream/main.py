from pyflink.common import Configuration
from pyflink.datastream import StreamExecutionEnvironment, RuntimeExecutionMode
from pyflink.datastream.connectors.kafka import KafkaSource, KafkaSink, KafkaRecordSerializationSchema, KafkaOffsetsInitializer

from pyflink.common.serialization import SimpleStringSchema
from pyflink.common.watermark_strategy import WatermarkStrategy
import os
import json
import pyflink

APPLICATION_PROPERTIES_FILE_PATH = "/etc/flink/application_properties.json"
is_local = True if os.environ.get("IS_LOCAL") else False

if is_local:
    APPLICATION_PROPERTIES_FILE_PATH = "application_properties.json"
    CURRENT_DIR = os.path.dirname(os.path.realpath(__file__))
    print("PyFlink home: " + os.path.dirname(os.path.abspath(pyflink.__file__)))
    print("Logging directory: " + os.path.dirname(os.path.abspath(pyflink.__file__)) + '/log')

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

def main():

    # Set up the execution environment
    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_runtime_mode(RuntimeExecutionMode.STREAMING)

    if is_local:
        env.set_parallelism(1)
        env.add_jars("file:///" + os.path.dirname(os.path.realpath(__file__)) + "/target/pyflink-dependencies.jar")

    props = get_application_properties()

    # Input Kafka configuration
    input_kafka_properties = property_map(props, "InputKafka0")
    input_topic = input_kafka_properties["topic"]
    input_bootstrap_servers = input_kafka_properties["bootstrap.servers"]
    input_group_id = input_kafka_properties["group.id"]

    # Output Kafka configuration
    output_kafka_properties = property_map(props, "OutputKafka0")
    output_topic = output_kafka_properties["topic"]
    output_bootstrap_servers = output_kafka_properties["bootstrap.servers"]

    # Keystore configuration
    kafka_config_provider_properties = property_map(props, "KafkaConfig")
    keystore_region = kafka_config_provider_properties["keystore.bucket.region"]
    keystore_bucket = kafka_config_provider_properties["keystore.bucket.name"]
    keystore_path = kafka_config_provider_properties["keystore.bucket.path"]
    keystore_pass_secret = kafka_config_provider_properties["keystore.password.secret"]
    keystore_pass_secret_field = kafka_config_provider_properties["keystore.password.secret.field"]

    # Truststore configuration
    # (if truststore is also required, uncomment the following 3 parameters)
    # truststore_region = kafka_config_provider_properties["truststore.bucket.region"]
    # truststore_bucket = kafka_config_provider_properties["truststore.bucket.name"]
    # truststore_path = kafka_config_provider_properties["truststore.bucket.path"]


    # Create Kafka Source
    # (We assume the truststore pwd is the default 'changeit'. If different, it can be retrieved from SecretsManager
    # similarly to the keystore pwd. We also assume that the keystore password and client key password are the same. If
    # this is not the case, two separate secrets can be used)
    kafka_consumer_properties = {
            'config.providers': 'secretsmanager,s3import',
            'config.providers.secretsmanager.class': 'com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider',
            'config.providers.ssm.class': 'com.amazonaws.kafka.config.providers.SsmParamStoreConfigProvider',
            'config.providers.s3import.class': 'com.amazonaws.kafka.config.providers.S3ImportConfigProvider',
            'security.protocol': 'SSL',
            'ssl.keystore.type': 'PKCS12',
            'ssl.keystore.location': f'${{s3import:{keystore_region}:{keystore_bucket}/{keystore_path}}}',
            'ssl.keystore.password': f'${{secretsmanager:{keystore_pass_secret}:{keystore_pass_secret_field}}}',
            'ssl.key.password': f'${{secretsmanager:{keystore_pass_secret}:{keystore_pass_secret_field}}}',
            # If truststore is also required, uncomment the following 3 configurations
            # 'ssl.truststore.type': 'PKCS12',
            # 'ssl.truststore.location': f'${{s3import:{truststore_region}:{truststore_bucket}/{truststore_path}}}',
            # 'ssl.truststore.password': 'changeit',
    }
    kafka_source = KafkaSource.builder() \
        .set_bootstrap_servers(input_bootstrap_servers) \
        .set_topics(input_topic) \
        .set_group_id(input_group_id) \
        .set_value_only_deserializer(SimpleStringSchema()) \
        .set_starting_offsets(KafkaOffsetsInitializer.earliest()) \
        .set_properties(kafka_consumer_properties) \
        .build()

    # Create a DataStream from the Kafka consumer
    stream = env.from_source(kafka_source, WatermarkStrategy.no_watermarks(), "Kafka source")

    # Create Kafka Sink
    # (note, KafkaSinkBuilder doesn't support set_properties() so we have to set every property with set_property())
    kafka_sink = KafkaSink.builder() \
        .set_bootstrap_servers(output_bootstrap_servers) \
        .set_record_serializer(
            KafkaRecordSerializationSchema.builder()
                .set_topic(output_topic)
                .set_value_serialization_schema(SimpleStringSchema())
                .build()

        ) \
        .set_property('config.providers', 'secretsmanager,s3import') \
        .set_property('config.providers.secretsmanager.class', 'com.amazonaws.kafka.config.providers.SecretsManagerConfigProvider') \
        .set_property('config.providers.ssm.class', 'com.amazonaws.kafka.config.providers.SsmParamStoreConfigProvider') \
        .set_property('config.providers.s3import.class', 'com.amazonaws.kafka.config.providers.S3ImportConfigProvider') \
        .set_property('security.protocol', 'SSL') \
        .set_property('ssl.keystore.type', 'PKCS12') \
        .set_property('ssl.keystore.location', f'${{s3import:{keystore_region}:{keystore_bucket}/{keystore_path}}}') \
        .set_property('ssl.keystore.password', f'${{secretsmanager:{keystore_pass_secret}:{keystore_pass_secret_field}}}') \
        .set_property('ssl.key.password', f'${{secretsmanager:{keystore_pass_secret}:{keystore_pass_secret_field}}}') \
        .build()


    # (If truststore is also required, add the following lines to the sink definition, above)
    #  .set_property('ssl.truststore.type', 'PKCS12') \
    #  .set_property('ssl.truststore.location', f'${{s3import:{truststore_region}:{truststore_bucket}/{truststore_path}}}') \
    #  .set_property('ssl.truststore.password', 'changeit') \

    # Attach the Kafka sink
    stream.sink_to(kafka_sink)

    # Execute the job
    env.execute("Kafka to Kafka Job")


if __name__ == "__main__":
    main()
