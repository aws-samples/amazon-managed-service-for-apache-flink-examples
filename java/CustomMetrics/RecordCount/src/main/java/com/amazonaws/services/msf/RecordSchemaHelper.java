    package com.amazonaws.services.msf;
     
    import com.fasterxml.jackson.core.type.TypeReference;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import java.io.IOException;
    import java.util.HashMap;
    import java.util.Map;
     
    public class RecordSchemaHelper {
     
        private static final double MIN_SPEED = 106.0D;
        private static final double MAX_SPEED = 245.0D;
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
     
        public static Map<String, Object> convertJsonStringToMap(String inputString) throws IOException {
            TypeReference<HashMap<String, Object>> typeReference = new TypeReference<>() {
            };
            return OBJECT_MAPPER.readValue(inputString, typeReference);
        }
     
        public static boolean isGreaterThanMinSpeed(String inputString) throws IOException {
            Map<String, Object> recordSchema = convertJsonStringToMap(inputString);
            return (Double) recordSchema.get("speed") > 106.0D;
        }
     
        public static boolean isLessThanMaxSpeed(String inputString) throws IOException {
            Map<String, Object> recordSchema = convertJsonStringToMap(inputString);
            return (Double) recordSchema.get("speed") < 245.0D;
        }
    }
