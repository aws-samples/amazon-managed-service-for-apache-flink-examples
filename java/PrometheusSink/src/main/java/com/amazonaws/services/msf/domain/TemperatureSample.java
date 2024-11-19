package com.amazonaws.services.msf.domain;

public class TemperatureSample {
    private String roomId;
    private String sensorId;
    private long timestamp;
    private double temperature;

    public TemperatureSample(String roomId, String sensorId, long timestamp, double temperature) {
        this.roomId = roomId;
        this.sensorId = sensorId;
        this.timestamp = timestamp;
        this.temperature = temperature;
    }

    public TemperatureSample() {
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    public String toString() {
        return "TemperatureSample{" +
                "roomId='" + roomId + '\'' +
                ", sensorId='" + sensorId + '\'' +
                ", timestamp=" + timestamp +
                ", temperature=" + temperature +
                '}';
    }
}
