package com.amazonaws.services.msf;

public class IncomingEvent {

    final String message;

    @Override
    public String toString() {
        return "IncomingEvent{" +
                "message='" + message + '\'' +
                '}';
    }

    public IncomingEvent(String message) {
        this.message = message;
    }



}
