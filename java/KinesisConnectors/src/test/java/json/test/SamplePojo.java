package json.test;

import java.time.Instant;
import java.util.Objects;

public class SamplePojo {

//    @JsonProperty("metricId")
    private String metricId;

//    @JsonProperty("stationId")
    private String stationId;

//    @JsonProperty("timestamp")
//    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

//    @JsonProperty("component")
    private String component;

//    @JsonProperty("evseId")
    private int evseId;

//    @JsonProperty("connectorId")
    private int connectorId;

//    @JsonProperty("variable")
    private String variable;

//    @JsonProperty("valueStr")
    private String valueStr;

    // Getters and setters
    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public int getEvseId() {
        return evseId;
    }

    public void setEvseId(int evseId) {
        this.evseId = evseId;
    }

    public int getConnectorId() {
        return connectorId;
    }

    public void setConnectorId(int connectorId) {
        this.connectorId = connectorId;
    }

    public String getVariable() {
        return variable;
    }

    public void setVariable(String variable) {
        this.variable = variable;
    }

    public String getValueStr() {
        return valueStr;
    }

    public void setValueStr(String valueStr) {
        this.valueStr = valueStr;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SamplePojo that = (SamplePojo) o;
        return evseId == that.evseId && connectorId == that.connectorId && Objects.equals(metricId, that.metricId) && Objects.equals(stationId, that.stationId) && Objects.equals(timestamp, that.timestamp) && Objects.equals(component, that.component) && Objects.equals(variable, that.variable) && Objects.equals(valueStr, that.valueStr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricId, stationId, timestamp, component, evseId, connectorId, variable, valueStr);
    }

    @Override
    public String toString() {
        return "MetricData{" +
                "metricId='" + metricId + '\'' +
                ", stationId='" + stationId + '\'' +
                ", timestamp=" + timestamp +
                ", component='" + component + '\'' +
                ", evseId=" + evseId +
                ", connectorId=" + connectorId +
                ", variable='" + variable + '\'' +
                ", valueStr='" + valueStr + '\'' +
                '}';
    }
}
