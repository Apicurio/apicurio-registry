package io.apicurio.registry.utils;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringUtilTest {

    @Test
    public void testAsLowerCase() {
        Assertions.assertNull(StringUtil.asLowerCase(null));
        Assertions.assertEquals("hello", StringUtil.asLowerCase("HELLO"));
        Assertions.assertEquals("test", StringUtil.asLowerCase("Test"));
    }

    @Test
    public void testLimitStr() {
        Assertions.assertNull(StringUtil.limitStr(null, 10));
        Assertions.assertEquals("", StringUtil.limitStr("", 10));
        Assertions.assertEquals("hello", StringUtil.limitStr("hello", 10));
        Assertions.assertEquals("hello", StringUtil.limitStr("hello", 5));
        Assertions.assertEquals("hel", StringUtil.limitStr("hello", 3));
        Assertions.assertEquals("he...", StringUtil.limitStr("hello world", 5, true));
        Assertions.assertEquals("...", StringUtil.limitStr("hello world", 3, true));
        Assertions.assertEquals("..", StringUtil.limitStr("hello world", 2, true));
        Assertions.assertEquals(".", StringUtil.limitStr("hello world", 1, true));
        Assertions.assertEquals("", StringUtil.limitStr("hello world", 0, true));
        Assertions.assertEquals("", StringUtil.limitStr("hello world", -1, true));
        Assertions.assertEquals("", StringUtil.limitStr("hello world", 0, false));
        Assertions.assertEquals("", StringUtil.limitStr("hello world", -5, false));
    }

    @Test
    public void testToReadableString() {
        Assertions.assertEquals("[]", StringUtil.toReadableString(null));
        Assertions.assertEquals("[]", StringUtil.toReadableString(new byte[0]));
        Assertions.assertEquals("[abc]", StringUtil.toReadableString("abc".getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals("[\\00\\01]", StringUtil.toReadableString(new byte[] { 0, 1 }));
    }
}
