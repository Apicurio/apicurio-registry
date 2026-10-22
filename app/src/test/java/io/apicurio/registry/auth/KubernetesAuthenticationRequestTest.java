/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesAuthenticationRequestTest {

    @Test
    void testBasicProperties() {
        Set<String> groups = Set.of("group1", "group2");
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "user1", "uid-123", groups);

        assertEquals("user1", request.getUsername());
        assertEquals("uid-123", request.getUid());
        assertEquals(2, request.getGroups().size());
        assertTrue(request.getGroups().contains("group1"));
        assertTrue(request.getGroups().contains("group2"));
    }

    @Test
    void testGroupsAreUnmodifiable() {
        Set<String> groups = new HashSet<>();
        groups.add("group1");
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "user1", "uid-123", groups);

        assertThrows(UnsupportedOperationException.class, () -> request.getGroups().add("group3"));
    }

    @Test
    void testNullGroupsBecomesEmpty() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "user1", "uid-123", null);

        assertTrue(request.getGroups().isEmpty());
    }

    @Test
    void testToStringDoesNotExposeToken() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "user1", "uid-123", Set.of("group1"));

        String str = request.toString();
        assertTrue(str.contains("user1"));
        assertTrue(str.contains("uid-123"));
        assertTrue(str.contains("group1"));
    }
}
