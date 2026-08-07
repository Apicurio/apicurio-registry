package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import org.junit.jupiter.api.Test;

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

    @Test
    void testCreateClientWithExplicitUrlVersion3() {
        Map<String, Object> props = new HashMap<>();
        props.put(SchemaResolverConfig.REGISTRY_URL, "http://apicurio:8081/apis/registry/v2");
        props.put(SchemaResolverConfig.REGISTRY_URL_VERSION, "3");

        RegistryClientFacade facade = RegistryClientFacadeFactory.create(new SchemaResolverConfig(props));
        assertInstanceOf(RegistryClientFacadeImpl.class, facade);
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
}
