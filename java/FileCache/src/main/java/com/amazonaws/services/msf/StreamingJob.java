package com.amazonaws.services.msf;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.File;


public class StreamingJob {
    private static final Logger LOG = LoggerFactory.getLogger(StreamingJob.class);

    private static final String JOBMANAGER_LOCAL_FILE_DIRECTORY = "/tmp/";

    public static final String DISTRIBUTED_CACHE_RESOURCE_NAME = "cacerts-file";

    private static DataGeneratorSource<Long> createDatagenSource() {
        return new DataGeneratorSource<>(
                i -> i,
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(1),
                TypeInformation.of(Long.class));
    }

    private static void downloadFileToDirectory(String s3BucketName, String s3FileKey, String targetDir) throws Exception {
        File tmpLocalDirectoryLocation = new File(targetDir);
        if (!tmpLocalDirectoryLocation.exists()) {
            tmpLocalDirectoryLocation.mkdirs();
        }

        File targetFile = new File(targetDir + "/" + s3FileKey);

        try (S3Client s3 = S3Client.create()) {
            s3.getObject(GetObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3FileKey)
                    .build(), targetFile.toPath());
            LOG.info("Copied S3 file s3://{}/{} to location {} ", s3BucketName, s3FileKey, targetFile);
        }
    }

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Download file to temporary directory on the JobManager
        String s3BucketName = "my-bucket-name";
        String s3FileKey = "file-key";
        downloadFileToDirectory(s3BucketName, s3FileKey, JOBMANAGER_LOCAL_FILE_DIRECTORY);
        // Register file with Flink distributed cache so that it can be access from TaskManagers
        env.registerCachedFile(JOBMANAGER_LOCAL_FILE_DIRECTORY + "/" + s3FileKey, DISTRIBUTED_CACHE_RESOURCE_NAME);


        env.fromSource(createDatagenSource(),
                        WatermarkStrategy.noWatermarks(),
                        "Datagen source",
                        TypeInformation.of(Long.class))
                // Add a pass-through function to retrieve the file from distributed cache and copy over to taskmanager local directory
                .map(new CopyingPassThroughFunction<>())
                .sinkTo(new DiscardingSink<>());

        env.execute("File Cache usage example");
    }
}
