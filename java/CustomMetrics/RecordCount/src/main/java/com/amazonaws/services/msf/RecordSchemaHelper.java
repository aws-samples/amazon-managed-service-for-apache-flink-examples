package com.amazonaws.services.msf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class RecordSchemaHelper {

    private static final TypeReference<HashMap<String, String>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final double MIN_SPEED = 106.0D;
    private static final double MAX_SPEED = 245.0D;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static boolean isGreaterThanMinSpeed(String inputString) throws IOException {
        Map<String, String> recordSchema = convertJsonStringToMap(inputString);
        return Double.parseDouble(recordSchema.get("speed")) > MIN_SPEED;
    }

    public static boolean isLessThanMaxSpeed(String inputString) throws IOException {
        Map<String, String> recordSchema = convertJsonStringToMap(inputString);
        return Double.parseDouble(recordSchema.get("speed")) < MAX_SPEED;
    }

    private static Map<String, String> convertJsonStringToMap(String inputString) throws IOException {
        return OBJECT_MAPPER.readValue(inputString, TYPE_REFERENCE);
    }

}
