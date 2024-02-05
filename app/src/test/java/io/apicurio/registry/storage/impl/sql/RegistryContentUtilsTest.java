/*
 * Copyright 2020 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * @author eric.wittmann@gmail.com
 */
class RegistryContentUtilsTest {


    @Test
    void testSerializeLabels() {
        List<String> labels = Collections.singletonList("foo");
        String actual = RegistryContentUtils.serializeLabels(labels);
        Assertions.assertEquals("[\"foo\"]", actual);
        
        labels = new ArrayList<String>();
        labels.add("one");
        labels.add("two");
        labels.add("three");
        actual = RegistryContentUtils.serializeLabels(labels);
        Assertions.assertEquals("[\"one\",\"two\",\"three\"]", actual);
    }


    @Test
    void testDeserializeLabels() {
        String labelsStr = "[\"one\",\"two\",\"three\"]";
        List<String> actual = RegistryContentUtils.deserializeLabels(labelsStr);
        Assertions.assertNotNull(actual);
        List<String> expected = new ArrayList<String>();
        expected.add("one");
        expected.add("two");
        expected.add("three");
        Assertions.assertEquals(expected, actual);
    }


    @Test
    void testSerializeProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("one", "1");
        props.put("two", "2");
        props.put("three", "3");
        String actual = RegistryContentUtils.serializeProperties(props);
        String expected = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Assertions.assertEquals(expected, actual);
    }


    @Test
    void testDeserializeProperties() {
        String propsStr = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Map<String, String> actual = RegistryContentUtils.deserializeProperties(propsStr);
        Assertions.assertNotNull(actual);
        Map<String, String> expected = new HashMap<>();
        expected.put("one", "1");
        expected.put("two", "2");
        expected.put("three", "3");
        Assertions.assertEquals(expected, actual);
    }

}
