package org.apache.flink.runtime.util;

import org.apache.hadoop.conf.Configuration;

public class HadoopUtils {
    public static Configuration getHadoopConfiguration(
            org.apache.flink.configuration.Configuration flinkConfiguration) {
                return new Configuration(false);
            }
        }

//public class HadoopUtils {
//    public static Configuration getHadoopConfiguration(
//            org.apache.flink.configuration.Configuration flinkConfiguration) {
//        // Create a basic Configuration
//        Configuration conf = new Configuration(true); // true to load default resources
//        
//        // Manually set HDFS-related properties
//        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
//        conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
//        
//        // S3 configuration
//        //conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
//        //conf.set("fs.s3.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
//        
//        return conf;
//    }
//}