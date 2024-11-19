package com.amazonaws.services.msf;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.formats.avro.typeutils.GenericRecordAvroTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Preconditions;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.amazonaws.services.msf.avro.AvroSchemaUtils;
import com.amazonaws.services.msf.datagen.AvroGenericStockTradeGeneratorFunction;
import com.amazonaws.services.msf.iceberg.IcebergSinkBuilder;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;


public class StreamingJob {
    private final static Logger LOG = LoggerFactory.getLogger(StreamingJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";

    private static boolean isLocal(StreamExecutionEnvironment env) {
        return env instanceof LocalStreamEnvironment;
    }

    /**
     * Load application properties from Amazon Managed Service for Apache Flink runtime or from a local resource, when the environment is local
     */
    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (isLocal(env)) {
            LOG.info("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    Objects.requireNonNull(StreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)).getPath());
        } else {
            LOG.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }


    // Data Generator source generating random trades as AVRO GenericRecords
    private static DataGeneratorSource<GenericRecord> createDataGenerator(Properties generatorProperties, Schema avroSchema) {
        double recordsPerSecond = Double.parseDouble(generatorProperties.getProperty("records.per.sec", "10.0"));
        Preconditions.checkArgument(recordsPerSecond > 0, "Generator records per sec must be > 0");

        LOG.info("Data generator: {} record/sec", recordsPerSecond);
        return new DataGeneratorSource<>(
                new AvroGenericStockTradeGeneratorFunction(avroSchema),
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(recordsPerSecond),
                new GenericRecordAvroTypeInfo(avroSchema)
        );
    }


    public static void main(String[] args) throws Exception {
        // Set up the streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        Map<String, Properties> applicationProperties = loadApplicationProperties(env);


        // Get AVRO Schema from the definition bundled with the application
        // Note that the application must "knows" the AVRO schema upfront, i.e. the schema must be either embedded
        // with the application or fetched at start time.
        // If the schema of the records received by the source changes, all changes must be FORWARD compatible.
        // This way, the application will be able to write the data with the new schema into the old schema, but schema
        // changes are not propagated to the Iceberg table.
        Schema avroSchema = AvroSchemaUtils.loadSchema();

        // Create Generic Record TypeInfo from schema.
        GenericRecordAvroTypeInfo avroTypeInfo = new GenericRecordAvroTypeInfo(avroSchema);

        // Local dev specific settings
        if (isLocal(env)) {
            // Checkpointing and parallelism are set by Amazon Managed Service for Apache Flink when running on AWS
            env.enableCheckpointing(10000);
            env.setParallelism(2);
        }

        // Data Generator Source.
        // Simulates an external source that receives AVRO Generic Records
        Properties dataGeneratorProperties = applicationProperties.get("DataGen");
        DataStream<GenericRecord> genericRecordDataStream = env.fromSource(
                createDataGenerator(dataGeneratorProperties, avroSchema),
                WatermarkStrategy.noWatermarks(),
                "DataGen");

        // Flink Sink Builder
        Properties icebergProperties = applicationProperties.get("Iceberg");
        FlinkSink.Builder icebergSinkBuilder = IcebergSinkBuilder.createBuilder(
                icebergProperties,
                genericRecordDataStream, avroSchema);
        // Sink to Iceberg Table
        icebergSinkBuilder.append();

        env.execute("Flink DataStream Sink");
    }
}