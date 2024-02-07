package io.apicurio.registry.storage.impl.sql;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SqlUtilTest {

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#serializeLabels(java.util.Map)}.
     */
    @Test
    void testSerializeLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("one", "1");
        labels.put("two", "2");
        labels.put("three", "3");
        String actual = SqlUtil.serializeLabels(labels);
        String expected = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#deserializeLabels(java.lang.String)}.
     */
    @Test
    void testDeserializeLabels() {
        String labelsStr = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Map<String, String> actual = SqlUtil.deserializeLabels(labelsStr);
        Assertions.assertNotNull(actual);
        Map<String, String> expected = new HashMap<>();
        expected.put("one", "1");
        expected.put("two", "2");
        expected.put("three", "3");
        Assertions.assertEquals(expected, actual);
    }

}
