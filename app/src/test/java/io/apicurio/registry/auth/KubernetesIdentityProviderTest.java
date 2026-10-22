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

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesIdentityProviderTest {

    private final KubernetesIdentityProvider provider = new KubernetesIdentityProvider();

    @Test
    void testGetRequestType() {
        assertEquals(KubernetesAuthenticationRequest.class, provider.getRequestType());
    }

    @Test
    void testAuthenticateCreatesIdentityWithPrincipal() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "system:serviceaccount:default:my-sa", "abc-123",
                Set.of("system:serviceaccounts", "developers"));

        SecurityIdentity identity = provider.authenticate(request, null).await().indefinitely();

        assertNotNull(identity);
        assertEquals("system:serviceaccount:default:my-sa", identity.getPrincipal().getName());
    }

    @Test
    void testAuthenticateAddsGroupsAsRoles() {
        Set<String> groups = Set.of("system:serviceaccounts", "developers", "team-alpha");
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "testuser", "uid-456", groups);

        SecurityIdentity identity = provider.authenticate(request, null).await().indefinitely();

        Set<String> roles = identity.getRoles();
        assertEquals(3, roles.size());
        assertTrue(roles.contains("system:serviceaccounts"));
        assertTrue(roles.contains("developers"));
        assertTrue(roles.contains("team-alpha"));
    }

    @Test
    void testAuthenticateAddsKubernetesCredential() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "testuser", "uid-789", Set.of("group1"));

        SecurityIdentity identity = provider.authenticate(request, null).await().indefinitely();

        KubernetesCredential credential = identity.getCredential(KubernetesCredential.class);
        assertNotNull(credential);
        assertEquals("testuser", credential.getUsername());
        assertEquals("uid-789", credential.getUid());
    }

    @Test
    void testAuthenticateWithEmptyGroups() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "testuser", "uid-000", Set.of());

        SecurityIdentity identity = provider.authenticate(request, null).await().indefinitely();

        assertNotNull(identity);
        assertTrue(identity.getRoles().isEmpty());
    }

    @Test
    void testAuthenticateWithNullGroups() {
        KubernetesAuthenticationRequest request = new KubernetesAuthenticationRequest(
                "testuser", "uid-000", null);

        SecurityIdentity identity = provider.authenticate(request, null).await().indefinitely();

        assertNotNull(identity);
        assertTrue(identity.getRoles().isEmpty());
    }
}
