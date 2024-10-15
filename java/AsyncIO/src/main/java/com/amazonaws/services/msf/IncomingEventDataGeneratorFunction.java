package com.amazonaws.services.msf;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.util.UUID;

class IncomingEventDataGeneratorFunction implements GeneratorFunction<Long, IncomingEvent> {

    @Override
    public IncomingEvent map(Long aLong) {

        String message = "Hello World";

        IncomingEvent incomingEvent =  new IncomingEvent(message);
        incomingEvent.setId(UUID.randomUUID().toString().replace("-", ""));

        return incomingEvent;
    }

}
