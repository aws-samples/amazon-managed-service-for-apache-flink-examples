package org.apache.flink.runtime.util;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.util.FlinkRuntimeException;
import org.apache.flink.util.Preconditions;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.apache.hadoop.util.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;


/**
 * This class is a copy of org.apache.flink.runtime.util.HadoopUtils with the getHadoopConfiguration() method replaced to
 * return an org.apache.hadoop.conf.Configuration instead of org.apache.hadoop.hdfs.HdfsConfiguration.
 *
 * This class is then shaded, along with org.apache.hadoop.conf.*, to avoid conflicts with the same classes provided by
 * org.apache.flink:flink-s3-fs-hadoop, which is normally installed as plugin in Flink when S3.
 *
 * Other methods are copied from the original class.
 */
public class HadoopUtils {
    private static final Logger LOG = LoggerFactory.getLogger(HadoopUtils.class);

    static final Text HDFS_DELEGATION_TOKEN_KIND = new Text("HDFS_DELEGATION_TOKEN");

    /**
     * This method has been re-implemented to always return a org.apache.hadoop.conf.Configuration
     */
    public static Configuration getHadoopConfiguration(
            org.apache.flink.configuration.Configuration flinkConfiguration) {
        return new Configuration(false);
    }

    public static boolean isKerberosSecurityEnabled(UserGroupInformation ugi) {
        return UserGroupInformation.isSecurityEnabled()
                && ugi.getAuthenticationMethod()
                == UserGroupInformation.AuthenticationMethod.KERBEROS;
    }


    public static boolean areKerberosCredentialsValid(
            UserGroupInformation ugi, boolean useTicketCache) {
        Preconditions.checkState(isKerberosSecurityEnabled(ugi));

        // note: UGI::hasKerberosCredentials inaccurately reports false
        // for logins based on a keytab (fixed in Hadoop 2.6.1, see HADOOP-10786),
        // so we check only in ticket cache scenario.
        if (useTicketCache && !ugi.hasKerberosCredentials()) {
            if (hasHDFSDelegationToken(ugi)) {
                LOG.warn(
                        "Hadoop security is enabled but current login user does not have Kerberos credentials, "
                                + "use delegation token instead. Flink application will terminate after token expires.");
                return true;
            } else {
                LOG.error(
                        "Hadoop security is enabled, but current login user has neither Kerberos credentials "
                                + "nor delegation tokens!");
                return false;
            }
        }

        return true;
    }

    /**
     * Indicates whether the user has an HDFS delegation token.
     */
    public static boolean hasHDFSDelegationToken(UserGroupInformation ugi) {
        Collection<Token<? extends TokenIdentifier>> usrTok = ugi.getTokens();
        for (Token<? extends TokenIdentifier> token : usrTok) {
            if (token.getKind().equals(HDFS_DELEGATION_TOKEN_KIND)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the Hadoop dependency is at least the given version.
     */
    public static boolean isMinHadoopVersion(int major, int minor) throws FlinkRuntimeException {
        final Tuple2<Integer, Integer> hadoopVersion = getMajorMinorBundledHadoopVersion();
        int maj = hadoopVersion.f0;
        int min = hadoopVersion.f1;

        return maj > major || (maj == major && min >= minor);
    }

    /**
     * Checks if the Hadoop dependency is at most the given version.
     */
    public static boolean isMaxHadoopVersion(int major, int minor) throws FlinkRuntimeException {
        final Tuple2<Integer, Integer> hadoopVersion = getMajorMinorBundledHadoopVersion();
        int maj = hadoopVersion.f0;
        int min = hadoopVersion.f1;

        return maj < major || (maj == major && min < minor);
    }

    private static Tuple2<Integer, Integer> getMajorMinorBundledHadoopVersion() {
        String versionString = VersionInfo.getVersion();
        String[] versionParts = versionString.split("\\.");

        if (versionParts.length < 2) {
            throw new FlinkRuntimeException(
                    "Cannot determine version of Hadoop, unexpected version string: "
                            + versionString);
        }

        int maj = Integer.parseInt(versionParts[0]);
        int min = Integer.parseInt(versionParts[1]);
        return Tuple2.of(maj, min);
    }
}
