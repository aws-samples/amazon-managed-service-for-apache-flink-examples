package com.amazonaws.services.msf;

public enum ProcessingOutcome {
    SUCCESS("SUCCESS"),
    ERROR("ERROR");

    private final String text;

    ProcessingOutcome(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
