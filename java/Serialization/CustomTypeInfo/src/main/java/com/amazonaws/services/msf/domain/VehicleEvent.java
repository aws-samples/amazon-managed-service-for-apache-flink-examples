package com.amazonaws.services.msf.domain;

import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.ListTypeInfo;
import org.apache.flink.api.java.typeutils.MapTypeInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Record representing an event sent by a connected vehicle.
 * The object follows POJO standards.
 * <p>
 * What's relevant to this example is that the object also contains a Map property and a List property.
 * <p>
 * This class demonstrates two alternative methods fir defining custom TypeInformation:
 * 1. Defining a TypeInfoFactory for each field that requires type information, in
 * 2. Defining a single TypeInfoFactory for the entire POJO class (commented out)
 */
// @TypeInfo(VehicleEvent.VehicleEventTypeInfoFactory.class)
public class VehicleEvent {
    private String vin;
    private long timestamp;

    @TypeInfo(SensorDataTypeInfoFactory.class)
    private Map<String, Long> sensorData = new HashMap<>();

    @TypeInfo(WariningLightsTypeInfoFactory.class)
    private List<String> warningLights = new ArrayList<>();

    /// Custom TypeInformation factories

    public static class SensorDataTypeInfoFactory extends TypeInfoFactory<Map<String, Long>> {
        @Override
        public TypeInformation<Map<String, Long>> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
            return new MapTypeInfo<>(Types.STRING, Types.LONG);
        }
    }

    ;

    public static class WariningLightsTypeInfoFactory extends TypeInfoFactory<List<String>> {
        @Override
        public TypeInformation<List<String>> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
            return new ListTypeInfo<>(Types.STRING);
        }
    }

    // As an alternative, you can define a single TypeInfoFactory for the whole POJO.
    // This requires setting the @TypeInfo at class level.
    // The two methods are alternative and mutually exclusive.
//    public static class VehicleEventTypeInfoFactory extends TypeInfoFactory<VehicleEvent> {
//        @Override
//        public TypeInformation<VehicleEvent> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
//            return Types.POJO(
//                    VehicleEvent.class,
//                    Map.of(
//                            "vin", Types.STRING,
//                            "timestamp", Types.LONG,
//                            "sensorData", new MapTypeInfo<>(Types.STRING, Types.LONG),
//                            "warningLights", new ListTypeInfo<>(Types.STRING)
//                    ));
//        }
//    }


    /// Constructors and accessors

    public VehicleEvent() {
    }

    public VehicleEvent(String vin, long timestamp, Map<String, Long> sensorData, List<String> warningLights) {
        this.vin = vin;
        this.timestamp = timestamp;
        this.sensorData = sensorData;
        this.warningLights = warningLights;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Long> getSensorData() {
        return sensorData;
    }

    public void setSensorData(Map<String, Long> sensorData) {
        this.sensorData = sensorData;
    }

    public List<String> getWarningLights() {
        return warningLights;
    }

    public void setWarningLights(List<String> warningLights) {
        this.warningLights = warningLights;
    }

    @Override
    public String toString() {
        return "VehicleEvent{" +
                "vin='" + vin + '\'' +
                ", timestamp=" + timestamp +
                ", sensorData=" + sensorData +
                ", warningLights=" + warningLights +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VehicleEvent that = (VehicleEvent) o;
        return timestamp == that.timestamp && Objects.equals(vin, that.vin) && Objects.equals(sensorData, that.sensorData) && Objects.equals(warningLights, that.warningLights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vin, timestamp, sensorData, warningLights);
    }
}
