package com.amazonaws.services.msf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.function.Function;

public class ConfigurationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationHelper.class);

    /**
     * Generic method to extract and parse numeric parameters from Properties
     * All parameters are validated to be positive (> 0)
     *
     * @param properties The Properties object to extract from
     * @param parameterName The name of the parameter to extract
     * @param defaultValue The default value to use if parameter is missing or invalid
     * @param parser Function to parse the string value to the desired numeric type
     * @param <T> The numeric type (Integer, Long, etc.)
     * @return The extracted and validated value or the default value
     */
    private static <T extends Number> T extractNumericParameter(
            Properties properties,
            String parameterName,
            T defaultValue,
            Function<String, T> parser) {

        String parameterValue = properties.getProperty(parameterName);
        if (parameterValue == null || parameterValue.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            T value = parser.apply(parameterValue.trim());
            
            if (value.doubleValue() <= 0) {
                throw new IllegalArgumentException(
                    String.format("Parameter %s with value %s must be positive", parameterName, value));
            }
            
            return value;
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid {} value: '{}'. Must be a valid positive {} value. Using default: {}",
                     parameterName, parameterValue, defaultValue.getClass().getSimpleName().toLowerCase(), defaultValue);
            return defaultValue;
        }
    }

    /**
     * Extract a required string parameter from Properties
     * Throws IllegalArgumentException if the parameter is missing or empty
     *
     * @param properties The Properties object to extract from
     * @param parameterName The name of the parameter to extract
     * @param errorMessage The error message to use if parameter is missing
     * @return The extracted string value (never null or empty)
     * @throws IllegalArgumentException if the parameter is missing or empty
     */
    static String extractRequiredStringParameter(Properties properties, String parameterName, String errorMessage) {
        String parameterValue = properties.getProperty(parameterName);
        if (parameterValue == null || parameterValue.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return parameterValue.trim();
    }

    /**
     * Extract an optional string parameter from Properties with default fallback
     *
     * @param properties The Properties object to extract from
     * @param parameterName The name of the parameter to extract
     * @param defaultValue The default value to use if parameter is missing or empty
     * @return The extracted string value or the default value
     */
    static String extractStringParameter(Properties properties, String parameterName, String defaultValue) {
        String parameterValue = properties.getProperty(parameterName);
        if (parameterValue == null || parameterValue.trim().isEmpty()) {
            return defaultValue;
        }
        return parameterValue.trim();
    }

    /**
     * Extract an integer parameter from Properties with validation and default fallback
     * The parameter must be positive (> 0)
     *
     * @param properties The Properties object to extract from
     * @param parameterName The name of the parameter to extract
     * @param defaultValue The default value to use if parameter is missing or invalid
     * @return The extracted integer value or the default value
     */
    static int extractIntParameter(Properties properties, String parameterName, int defaultValue) {
        return extractNumericParameter(properties, parameterName, defaultValue, Integer::parseInt);
    }

    /**
     * Extract a long parameter from Properties with validation and default fallback
     * The parameter must be positive (> 0)
     *
     * @param properties The Properties object to extract from
     * @param parameterName The name of the parameter to extract
     * @param defaultValue The default value to use if parameter is missing or invalid
     * @return The extracted long value or the default value
     */
    static long extractLongParameter(Properties properties, String parameterName, long defaultValue) {
        return extractNumericParameter(properties, parameterName, defaultValue, Long::parseLong);
    }
}
