package com.amazonaws.services.msf;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// Custom deserialization schema for handling multiple generic Avro record types
class OptionDeserializationSchema implements KafkaRecordDeserializationSchema<Option> {

    private final String schemaRegistryUrl;
    private transient KafkaAvroDeserializer deserializer;

    public OptionDeserializationSchema(String schemaRegistryUrl) {
        this.schemaRegistryUrl = schemaRegistryUrl;
    }

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
        SchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(schemaRegistryUrl, 100);

        // With following configuration Avro deserializer should figure out the target type
        // based on the schema subject name
        // (which is the generated class's name for the corresponding avro record type).
        Map<String, Object> config = new HashMap<>();
        config.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy.class.getName());
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        // Create and configure the deserializer
        deserializer = new KafkaAvroDeserializer(schemaRegistryClient, config);
        deserializer.configure(config, false);
    }

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<Option> out) throws IOException {
        Object deserialized = deserializer.deserialize(record.topic(), record.value());
        out.collect(new Option(deserialized));
    }

    @Override
    public TypeInformation<Option> getProducedType() {
        return TypeInformation.of(Option.class);
    }
}
