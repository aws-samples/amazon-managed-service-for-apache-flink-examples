package com.amazonaws.services.msf.beam;

import org.apache.beam.sdk.transforms.DoFn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PingPongFn extends DoFn<Document, Document> {
    private final Logger LOGGER = LogManager.getLogger(PingPongFn.class);

    /**
     * Example of DoFn. Replaces term ping to pong.
     *
     * @param c
     */
    @ProcessElement
    public void processElement(ProcessContext c) {
        // PCollection is immutable
        Document document = c.element();

        if (document.getText().trim().equalsIgnoreCase("ping")) {
            LOGGER.info("Ponged!");
            c.output(new Document("pong"));
        } else {
            LOGGER.info("No action for: {}", document.getText());
            c.output(document);
        }
    }
}
