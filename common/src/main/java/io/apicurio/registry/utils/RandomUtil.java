package io.apicurio.registry.utils;

import java.util.Random;

public final class RandomUtil {

    public static final Random RANDOM = new Random();

    public static int randomPort() {
        // See https://www.rfc-editor.org/rfc/rfc6335#section-6
        return RANDOM.nextInt(0xC000, 0xFFFF + 1);
    }

    private RandomUtil() {
    }
}
