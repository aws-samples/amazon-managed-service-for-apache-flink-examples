package com.amazonaws.services.msf.domain;

import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.MapTypeInfo;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Record representing aggregate events from connected vehicle.
 * <p>
 * Similarly to {@link VehicleEvent}, it demonstrates the use of custom TypeInformation.
 */
public class AggregateVehicleEvent {
    private String vin;
    private long timestamp;

    @TypeInfo(AverageSensorDataTypeInfoFactory.class)
    private Map<String, Long> averageSensorData = new HashMap<>();

    private int countDistinctWarnings;


    /// Custom TypeInformation factory

    public static class AverageSensorDataTypeInfoFactory extends TypeInfoFactory<Map<String, Long>> {
        @Override
        public TypeInformation<Map<String, Long>> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
            return new MapTypeInfo<>(Types.STRING, Types.LONG);
        }
    }

    /// Constructors and accessors


    public AggregateVehicleEvent() {
    }

    public AggregateVehicleEvent(String vin, long timestamp, Map<String, Long> averageSensorData, int countDistinctWarnings) {
        this.vin = vin;
        this.timestamp = timestamp;
        this.averageSensorData = averageSensorData;
        this.countDistinctWarnings = countDistinctWarnings;
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

    public Map<String, Long> getAverageSensorData() {
        return averageSensorData;
    }

    public void setAverageSensorData(Map<String, Long> averageSensorData) {
        this.averageSensorData = averageSensorData;
    }

    public int getCountDistinctWarnings() {
        return countDistinctWarnings;
    }

    public void setCountDistinctWarnings(int countDistinctWarnings) {
        this.countDistinctWarnings = countDistinctWarnings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregateVehicleEvent that = (AggregateVehicleEvent) o;
        return timestamp == that.timestamp && countDistinctWarnings == that.countDistinctWarnings && Objects.equals(vin, that.vin) && Objects.equals(averageSensorData, that.averageSensorData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vin, timestamp, averageSensorData, countDistinctWarnings);
    }

    @Override
    public String toString() {
        return "AggregateVehicleEvent{" +
                "vin='" + vin + '\'' +
                ", timestamp=" + timestamp +
                ", averageSensorData=" + averageSensorData +
                ", countDistinctWarnings=" + countDistinctWarnings +
                '}';
    }
}
