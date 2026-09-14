/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Predicate;

public class MockOAuth2TestResourceTest {

    private MockOAuth2TestResource resource;

    @BeforeEach
    public void setUp() {
        resource = new MockOAuth2TestResource();
    }

    @AfterEach
    public void tearDown() {
        if (resource != null) {
            resource.stop();
        }
    }

    @Test
    public void testStartAndStopDefaultConfig() {
        Map<String, String> props = resource.start();

        Assertions.assertNotNull(props);
        Assertions.assertTrue(props.containsKey("quarkus.oidc.auth-server-url"));
        Assertions.assertTrue(props.get("quarkus.oidc.auth-server-url").contains("/default"));
        Assertions.assertTrue(props.containsKey("quarkus.oidc.token-path"));
        Assertions.assertTrue(props.get("quarkus.oidc.token-path").contains("/default/token"));
        Assertions.assertEquals(MockOAuth2TestResource.DEFAULT_CLIENT_ID, props.get("quarkus.oidc.client-id"));
        Assertions.assertEquals(MockOAuth2TestResource.DEFAULT_CLIENT_SECRET, props.get("quarkus.oidc.credentials.secret"));
        Assertions.assertEquals("true", props.get("quarkus.oidc.tenant-enabled"));
        Assertions.assertEquals("true", props.get("apicurio.authn.basic-client-credentials.enabled"));
        Assertions.assertEquals("true", props.get("apicurio.auth.role-based-authorization"));
        Assertions.assertEquals(3600L, resource.getDefaultTokenExpirySeconds());

        resource.stop();
    }

    @Test
    public void testCustomInitArgs() {
        resource.init(Map.of(
                "issuer.id", "my-custom-issuer",
                "token.expiry", "7200"
        ));

        Map<String, String> props = resource.start();

        Assertions.assertNotNull(props);
        Assertions.assertTrue(props.get("quarkus.oidc.auth-server-url").contains("/my-custom-issuer"));
        Assertions.assertTrue(props.get("quarkus.oidc.token-path").contains("/my-custom-issuer/token"));
        Assertions.assertEquals(7200L, resource.getDefaultTokenExpirySeconds());

        resource.stop();
    }

    @Test
    public void testInvalidTokenExpiryFallsBackToDefault() {
        resource.init(Map.of("token.expiry", "invalid-number"));
        Assertions.assertEquals(3600L, resource.getDefaultTokenExpirySeconds());
    }

    @Test
    public void testFieldInjection() {
        resource.start();

        TestClass target = new TestClass();
        resource.inject(new QuarkusTestResourceLifecycleManager.TestInjector() {
            @Override
            public void injectIntoFields(Object instance, Predicate<Field> predicate) {
                for (Field field : target.getClass().getDeclaredFields()) {
                    if (predicate.test(field)) {
                        field.setAccessible(true);
                        try {
                            field.set(target, instance);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        });

        Assertions.assertNotNull(target.mockServer);
        Assertions.assertTrue(target.mockServer instanceof MockOAuth2Server);
    }

    private static class TestClass {
        @InjectMockOAuth2Server
        MockOAuth2Server mockServer;
    }
}