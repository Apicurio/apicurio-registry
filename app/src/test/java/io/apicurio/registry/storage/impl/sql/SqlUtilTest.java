package io.apicurio.registry.storage.impl.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SqlUtilTest {

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#serializeLabels(java.util.List)}.
     */
    @Test
    void testSerializeLabels() {
        List<String> labels = Collections.singletonList("foo");
        String actual = SqlUtil.serializeLabels(labels);
        Assertions.assertEquals("[\"foo\"]", actual);
        
        labels = new ArrayList<String>();
        labels.add("one");
        labels.add("two");
        labels.add("three");
        actual = SqlUtil.serializeLabels(labels);
        Assertions.assertEquals("[\"one\",\"two\",\"three\"]", actual);
    }

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#deserializeLabels(java.lang.String)}.
     */
    @Test
    void testDeserializeLabels() {
        String labelsStr = "[\"one\",\"two\",\"three\"]";
        List<String> actual = SqlUtil.deserializeLabels(labelsStr);
        Assertions.assertNotNull(actual);
        List<String> expected = new ArrayList<String>();
        expected.add("one");
        expected.add("two");
        expected.add("three");
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#serializeProperties(java.util.Map)}.
     */
    @Test
    void testSerializeProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("one", "1");
        props.put("two", "2");
        props.put("three", "3");
        String actual = SqlUtil.serializeProperties(props);
        String expected = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Test method for {@link io.apicurio.registry.storage.impl.sql.SqlUtil#deserializeProperties(java.lang.String)}.
     */
    @Test
    void testDeserializeProperties() {
        String propsStr = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Map<String, String> actual = SqlUtil.deserializeProperties(propsStr);
        Assertions.assertNotNull(actual);
        Map<String, String> expected = new HashMap<>();
        expected.put("one", "1");
        expected.put("two", "2");
        expected.put("three", "3");
        Assertions.assertEquals(expected, actual);
    }

}
