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
public class RegistryContentUtilsTest {

    @Test
    void testSerializeLabels() {
        Map<String, String> props = new HashMap<>();
        props.put("one", "1");
        props.put("two", "2");
        props.put("three", "3");
        String actual = RegistryContentUtils.serializeLabels(props);
        String expected = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testDeserializeLabels() {
        String propsStr = "{\"one\":\"1\",\"two\":\"2\",\"three\":\"3\"}";
        Map<String, String> actual = RegistryContentUtils.deserializeLabels(propsStr);
        Assertions.assertNotNull(actual);
        Map<String, String> expected = new HashMap<>();
        expected.put("one", "1");
        expected.put("two", "2");
        expected.put("three", "3");
        Assertions.assertEquals(expected, actual);
    }
}