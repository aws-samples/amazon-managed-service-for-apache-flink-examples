package com.amazonaws.services.msf;

import java.util.UUID;

public class IncomingEvent {

    final String message;
    String id;

    @Override
    public String toString() {
        return "IncomingEvent{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public IncomingEvent(String message) {
        this.message = message;
        this.id = UUID.randomUUID().toString().replace("-", "");

    }

    // Getter method
    public String getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }

}
