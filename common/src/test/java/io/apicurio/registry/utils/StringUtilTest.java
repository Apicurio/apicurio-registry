package io.apicurio.registry.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringUtilTest {

    @Test
    public void testAsLowerCase() {
        Assertions.assertNull(StringUtil.asLowerCase(null));
        Assertions.assertEquals("hello", StringUtil.asLowerCase("HELLO"));
        Assertions.assertEquals("test", StringUtil.asLowerCase("Test"));
    }
}
