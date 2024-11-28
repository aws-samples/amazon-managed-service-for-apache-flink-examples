package com.amazonaws.services.msf;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static com.amazonaws.services.msf.StreamingJob.DISTRIBUTED_CACHE_RESOURCE_NAME;

public class CopyingPassThroughFunction<T> extends RichMapFunction<T, T> {

    private static final Logger LOG = LoggerFactory.getLogger(CopyingPassThroughFunction.class);


    private static final String TASKMANAGER_LOCAL_FILE_LOCATION = "/tmp/cacerts";

    @Override
    public void open(OpenContext openContext) throws Exception {
        // Perform setup steps here
        // Copy file from distributed file cache to local directory on TaskManager if it doesn't already exist
        // This file was registered on the JobManager as part of the "main()" function
        File localFileDir = new File(TASKMANAGER_LOCAL_FILE_LOCATION);

        // Skip the copy if the file already exists locally
        if (!localFileDir.exists()) {
            localFileDir.mkdirs();
            File cachedFile = getRuntimeContext().getDistributedCache().getFile(DISTRIBUTED_CACHE_RESOURCE_NAME);
            Files.copy(cachedFile.toPath(), localFileDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Copied over resource file {} to taskmanager location {}", DISTRIBUTED_CACHE_RESOURCE_NAME, TASKMANAGER_LOCAL_FILE_LOCATION);
        } else {
            LOG.info("Skipping resource copy for resource {} on taskmanager as the file already exists: {}", DISTRIBUTED_CACHE_RESOURCE_NAME, TASKMANAGER_LOCAL_FILE_LOCATION);
        }

        // Perform any custom operations on the copied file here. For example, reconfiguring the trust store of the JVM
        LOG.info("Trust store location before modification : " + System.getProperty("javax.net.ssl.trustStore"));
        if (!TASKMANAGER_LOCAL_FILE_LOCATION.equals(System.getProperty("javax.net.ssl.trustStore"))) {
            System.setProperty("javax.net.ssl.trustStore", TASKMANAGER_LOCAL_FILE_LOCATION);
            LOG.info("Trust store location after modification : " + System.getProperty("javax.net.ssl.trustStore"));
        } else {
            LOG.info("Trust store already pointing to right location. Skipping modification");
        }
    }


    @Override
    public T map(T t) throws Exception {
        // simply pass through
        return t;
    }
}
