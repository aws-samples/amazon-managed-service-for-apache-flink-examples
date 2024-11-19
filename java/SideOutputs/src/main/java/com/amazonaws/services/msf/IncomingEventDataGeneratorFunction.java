package com.amazonaws.services.msf;

import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

class IncomingEventDataGeneratorFunction implements GeneratorFunction<Long, IncomingEvent> {

    private static final String[] MESSAGES = { "Hello World", "Poison" };

    @Override
    public IncomingEvent map(Long aLong) {
        int messageIndex = RandomUtils.nextInt(0, MESSAGES.length);
        String message = MESSAGES[messageIndex];

        return new IncomingEvent(
                message);
    }

}
