package com.amazonaws.services.msf;

import org.apache.flink.connector.datagen.source.GeneratorFunction;

class IncomingEventDataGeneratorFunction implements GeneratorFunction<Long, IncomingEvent> {

    @Override
    public IncomingEvent map(Long aLong) {

        String message = "Hello World";

        return new IncomingEvent(
                message
        );
    }

}
