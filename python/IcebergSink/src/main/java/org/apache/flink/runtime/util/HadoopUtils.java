// This utility is used by Flink to dynamically load Hadoop configurations at runtime.
// Why Needed: Required for Flink to interact with Hadoop-compatible systems (e.g., S3 via s3a:// or s3:// paths).
// Returns an empty Hadoop Configuration object (new Configuration(false)).

package org.apache.flink.runtime.util;

import org.apache.hadoop.conf.Configuration;

public class HadoopUtils {
    public static Configuration getHadoopConfiguration(
            org.apache.flink.configuration.Configuration flinkConfiguration) {
                return new Configuration(false);
            }
        }