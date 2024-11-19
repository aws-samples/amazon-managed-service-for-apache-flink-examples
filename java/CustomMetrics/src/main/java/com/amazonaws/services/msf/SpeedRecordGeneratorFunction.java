package com.amazonaws.services.msf;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

class SpeedRecordGeneratorFunction implements GeneratorFunction<Long, SpeedRecord> {

    @Override
    public SpeedRecord map(Long aLong) {
       return new SpeedRecord(
               RandomStringUtils.randomAlphabetic(8),
               RandomUtils.nextDouble(60,150)
       );
    }

}
