package com.amazonaws.services.msf;

public class ProcessedEvent {

    final String message;
    public String processed;

    @Override
    public String toString() {
        return "ProcessedEvent{" +
                ", message='" + message + '\'' +
                ", processed='" + processed + '\'' +
                '}';
    }

    public ProcessedEvent(String message, String processed) {
        this.message = message;
        this.processed = processed;
    }

    // Getter methods
    public String getMessage() {
        return message;
    }

    public String getProcessed() {
        return processed;
    }

    // Setter method for processed field
    public void setProcessed(String processed) {
        this.processed = processed;
    }

}
