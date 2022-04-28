package io.apicurio.registry.systemtest.framework;

import java.util.Random;

public class StringUtils {
    private static final int NUMERAL_ZERO = 48;
    private static final int NUMERAL_NINE = 57;
    private static final int UPPERCASE_A = 65;
    private static final int UPPERCASE_Z = 90;
    private static final int UNDERSCORE = 95;
    private static final int LOWERCASE_A = 97;
    private static final int LOWERCASE_Z = 122;

    public static String getRandom(int length) {
        return (new Random()).ints(NUMERAL_ZERO, LOWERCASE_Z + 1)
                .filter(i -> (i <= NUMERAL_NINE || i >= UPPERCASE_A) && (i <= UPPERCASE_Z || i == UNDERSCORE || i >= LOWERCASE_A))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
