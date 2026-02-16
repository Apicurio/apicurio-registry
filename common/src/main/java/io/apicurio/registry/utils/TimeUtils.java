package io.apicurio.registry.utils;

import java.time.Duration;

public final class TimeUtils {

    public static boolean isPositive(Duration duration) {
        // Duration.isPositive() is only available in Java 18+ :(
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
