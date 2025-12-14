package com.amazonaws.services.msf.beam;

import org.apache.beam.sdk.transforms.SimpleFunction;

import java.nio.charset.StandardCharsets;

public class ByteSerializer extends SimpleFunction<Document, byte[]> {
    /**
     * Takes Document object and serialize the text
     *
     * @param doc - Document object
     * @return serialized text that can be sinked to AWS Kinesis
     */
    @Override
    public byte[] apply(Document doc) {

        return doc.getText().getBytes(StandardCharsets.UTF_8);
    }
}
