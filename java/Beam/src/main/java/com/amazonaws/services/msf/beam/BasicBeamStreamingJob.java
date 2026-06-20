package com.amazonaws.services.msf.beam;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.beam.runners.flink.FlinkPipelineOptions;
import org.apache.beam.runners.flink.FlinkRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.aws2.kinesis.KinesisIO;
import org.apache.beam.sdk.io.aws2.kinesis.KinesisRecord;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.kinesis.common.InitialPositionInStream;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * Basic Apache Beam pipeline that runs on Managed Apache Flink
 */
public class BasicBeamStreamingJob {
    private static final Logger LOGGER = LogManager
            .getLogger(BasicBeamStreamingJob.class);

    // Name of the local JSON resource with the application properties in the same format as they are received from the Amazon Managed Service for Apache Flink runtime
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE
            = "flink-application-properties-dev.json";

    // Property name for the Input Stream
    private static final String INPUT_STREAM_PROPERTY_NAME =
            "InputStream0";

    // Property name for the Output Stream
    private static final String OUTPUT_STREAM_PROPERTY_NAME =
            "OutputStream0";

    /**
     * Load application properties from Amazon Managed Service for Apache
     * Flink runtime or from a local resource, when the environment is local.
     *
     * @param env - context of the flink environment that is executed on
     * @return properties that were read from either from local file or service
     * @throws IOException - thrown if the local properties file doesn't exist
     */
    private static Map<String, Properties> loadApplicationProperties(
            StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.info(
                    "Loading application properties from '{}'",
                    LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    BasicBeamStreamingJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE)
                            .getPath());
        } else {
            LOGGER.info("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    /***
     * Constructs an Apache Beam pipeline that can be executed by Apache Flink
     *
     * @param inputStream - input stream name that pipeline will source from
     * @param outputStream - output stream name that pipeline will sink to
     * @return constructed Apache Beam pipeline
     */
    public static Pipeline createFlinkPipeline(String inputStream,
                                           String outputStream) {

        // Apache Beam requires the runner to be set as Flink Runner explicitly.
        FlinkPipelineOptions options =
                PipelineOptionsFactory
                        .create()
                        .as(FlinkPipelineOptions.class);
        options.setRunner(FlinkRunner.class);

        Pipeline p =  Pipeline.create(options);
        PCollection<Document> record = p.apply(
                KinesisIO
                        .read()
                        .withStreamName(inputStream)
                        .withInitialPositionInStream(
                                InitialPositionInStream.LATEST)
        ).apply(
                MapElements
                        .into(TypeDescriptor.of(Document.class))
                        .via((KinesisRecord r)-> new Document(
                                new String(r.getDataAsBytes())))
        ).apply(
                ParDo.of(new PingPongFn())
        );

        record.apply(
                KinesisIO.<Document>write()
                        .withStreamName(outputStream)
                        .withSerializer(new ByteSerializer())
                        .withPartitioner(new HashKeyPartitioner()));

        return p;
    }

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment
                .getExecutionEnvironment();

        final Map<String, Properties> props = loadApplicationProperties(env);
        final String inputStream = props
                .get(INPUT_STREAM_PROPERTY_NAME)
                .getProperty("stream.name");
        final String outputStream = props
                .get(OUTPUT_STREAM_PROPERTY_NAME)
                .getProperty("stream.name");

        Pipeline p = createFlinkPipeline(inputStream, outputStream);

        p.run().waitUntilFinish();
    }
}
