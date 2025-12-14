package com.amazonaws.services.msf.beam;

import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.extensions.avro.coders.AvroCoder;

@DefaultCoder(AvroCoder.class)
public class Document {
    private final String text;

    // Default constructor
    public Document() {
        this.text = "";
    }

    public Document(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
