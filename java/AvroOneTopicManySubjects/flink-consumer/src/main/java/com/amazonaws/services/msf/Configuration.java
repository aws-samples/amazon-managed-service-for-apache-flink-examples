package com.amazonaws.services.msf;

import org.apache.flink.util.Preconditions;

import java.util.Properties;

public class Configuration {
    private final String bootstrapServers;
    private final String topic;
    private final String consumerGroupId;
    private final String schemaRegistryUrl;

    public Configuration(Properties properties) {
        this.bootstrapServers = Preconditions.checkNotNull(properties.getProperty("bootstrap.servers"), "bootstrap.servers not defined");
        this.topic = Preconditions.checkNotNull(properties.getProperty("source.topic"), "source.topic not defined");
        this.consumerGroupId = properties.getProperty("source.consumer.group.id", "flink-consumer");
        this.schemaRegistryUrl = properties.getProperty("schema.registry.url", "http://localhost:8085");
    }


    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getTopic() {
        return topic;
    }

    public String getConsumerGroupId() {
        return consumerGroupId;
    }

    public String getSchemaRegistryUrl() {
        return schemaRegistryUrl;
    }
}
