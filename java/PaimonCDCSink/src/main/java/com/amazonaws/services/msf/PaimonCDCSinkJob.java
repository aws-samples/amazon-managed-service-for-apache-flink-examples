package com.amazonaws.services.msf;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.paimon.flink.action.Action;
import org.apache.paimon.flink.action.ActionBase;
import org.apache.paimon.flink.action.ActionFactory;

import java.io.IOException;
import java.util.*;

public class PaimonCDCSinkJob {

    private static final Logger LOGGER = LogManager.getLogger(PaimonCDCSinkJob.class);
    private static final String LOCAL_APPLICATION_PROPERTIES_RESOURCE = "flink-application-properties-dev.json";
    private static final String SEP_KEY = "@_";
    private static final String ACTION_CONF_GROUP = "ActionConf";
    private static final String ACTION_KEY = "action";
    private static final String PARAM_KEY_PREFIX = "--";

    public static void main(String[] args) throws Exception{

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        Map<String, Properties> confMap = loadApplicationProperties(env);
        String[] actionArgs = configToActionParameters(confMap);
        if (actionArgs.length < 1) {
            LOGGER.error("No action specified");
            System.exit(1);
        }

        LOGGER.info("actionArgs: {}", Arrays.toString(actionArgs));

        Optional<Action> actionOpt = ActionFactory.createAction(actionArgs);

        if (actionOpt.isPresent()) {
            Action action = actionOpt.get();
            if (action instanceof ActionBase) {
                LOGGER.info("ActionBase: {}", action.getClass().getName());
                ((ActionBase) action).withStreamExecutionEnvironment(env).run();
            } else {
                action.run();
            }
        } else {
            LOGGER.info("No paimon flink action service found");
            System.exit(1);
        }
    }

    private static Map<String, Properties> loadApplicationProperties(StreamExecutionEnvironment env) throws IOException {
        if (env instanceof LocalStreamEnvironment) {
            LOGGER.debug("Loading application properties from '{}'", LOCAL_APPLICATION_PROPERTIES_RESOURCE);
            return KinesisAnalyticsRuntime.getApplicationProperties(
                    PaimonCDCSinkJob.class.getClassLoader()
                            .getResource(LOCAL_APPLICATION_PROPERTIES_RESOURCE).getPath());
        } else {
            LOGGER.debug("Loading application properties from Amazon Managed Service for Apache Flink");
            return KinesisAnalyticsRuntime.getApplicationProperties();
        }
    }

    private static String[] configToActionParameters(Map<String, Properties> confMap) {

        Properties actionProp = confMap.get(ACTION_CONF_GROUP);
        if (actionProp == null) {
            LOGGER.error("ActionConf not found in application properties");
            System.exit(1);
        }

        String action = actionProp.getProperty(ACTION_KEY);
        if (action == null || action.isEmpty()) {
            LOGGER.error("Action not found in application properties");
        }

        actionProp.remove(ACTION_KEY);

        List<String> params = new ArrayList<>();
        params.add(action);

        for (Map.Entry<String, Properties> confEntry : confMap.entrySet()) {
            confEntry.getValue().forEach(
                   (k, v) -> {
                       String ks = k.toString();
                       int idx = ks.indexOf(SEP_KEY);
                       String paramKey;
                       String paramVal;
                       if (idx != -1) {
                           paramKey = String.format("%s%s", PARAM_KEY_PREFIX , ks.substring(0, idx));
                           paramVal = String.format("%s=%s", ks.substring(idx  + SEP_KEY.length()), v);

                       } else {
                           paramKey = String.format("%s%s", PARAM_KEY_PREFIX , ks);
                           paramVal = v.toString();
                       }
                       params.add(paramKey);
                       params.add(paramVal);
                   }
            );
        }

        return params.toArray(new String[0]);
    }

}
