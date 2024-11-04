package com.amazonaws.services.msf;

import java.util.UUID;

public class IncomingEvent {

    final String message;
    private String id;

    @Override
    public String toString() {
        return "IncomingEvent{" +
                "id='" + id + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public IncomingEvent(String message) {
        this.message = message;

    }

    // Getter method
    public String getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

}
