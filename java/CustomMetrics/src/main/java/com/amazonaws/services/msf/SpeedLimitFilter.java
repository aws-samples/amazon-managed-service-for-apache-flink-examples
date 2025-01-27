package com.amazonaws.services.msf;

class SpeedLimitFilter {

    private static final double SPEED_LIMIT = 106.0D;

    static boolean isAboveSpeedLimit(SpeedRecord value) {
        return value.speed > SPEED_LIMIT;
    }

}
