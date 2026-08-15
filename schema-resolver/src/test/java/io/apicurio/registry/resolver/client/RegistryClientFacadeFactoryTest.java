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

package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RegistryClientFacadeFactoryTest {

    @Test
    void testCreateClientWithExplicitUrlVersion2() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, "2");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl_v2.class, facade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"V2", "v2", "2.0", " 2 ", "2 "})
    void testCreateClientWithExplicitUrlVersion2Variants(String version) {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, version);

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl_v2.class, facade);
    }

    @Test
    void testCreateClientWithExplicitUrlVersion3() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081/apis/registry/v2");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, "3");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl.class, facade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"V3", "v3", "3.0", " 3 ", "3 "})
    void testCreateClientWithExplicitUrlVersion3Variants(String version) {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081/apis/registry/v2");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, version);

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl.class, facade);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void testCreateClientWithEmptyUrlVersionFallsBackToAutoDetect(String version) {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081/apis/registry/v2");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, version);

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl_v2.class, facade);
    }

    @Test
    void testCreateClientAutoDetectV2FromUrl() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081/apis/registry/v2");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl_v2.class, facade);
    }

    @Test
    void testCreateClientDefaultToV3() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl.class, facade);
    }

    @Test
    void testCreateClientWithWorkloadIdentityOAuthNoSecret() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081");
        props.put(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, "http://auth-server/token");
        props.put(SchemaResolverConfig.AUTH_CLIENT_ID, "workload-client-id");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl.class, facade);
    }
}
