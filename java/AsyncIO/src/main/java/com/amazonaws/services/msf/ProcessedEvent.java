package com.amazonaws.services.msf;

public class ProcessedEvent {

    final String message;

    @Override
    public String toString() {
        return "ProcessedEvent{" +
                ", message='" + message + '\'' +
                '}';
    }

    public ProcessedEvent(String message) {
        this.message = message;
    }

    // Getter methods
    public String getMessage() {
        return message;
    }
}
