package io.apicurio.registry.storage.decorator;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.error.RegistryStorageException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegistryStorageProxyFactoryTest {

    @Test
    void unoverriddenMethodGoesDirectlyToRawStorage() {
        var raw = new StubStorage();
        var decorator = new TestDecorator();
        var proxy = RegistryStorageProxyFactory.createProxy(raw, List.of(decorator));

        // storageName() is not overridden by TestDecorator, so it should go to raw
        assertEquals("stub", proxy.storageName());
        assertEquals(1, raw.storageNameCallCount);
    }

    @Test
    void overriddenMethodGoesThoughDecorator() {
        var raw = new StubStorage();
        var decorator = new TestDecorator();
        var proxy = RegistryStorageProxyFactory.createProxy(raw, List.of(decorator));

        // isReadOnly() is overridden by TestDecorator
        assertTrue(proxy.isReadOnly());
        assertEquals(0, raw.isReadOnlyCallCount); // raw should NOT be called
    }

    @Test
    void correctOrderingWithMultipleDecorators() {
        var raw = new StubStorage();
        raw.configValue = "raw";

        var first = new ConfigCachingDecorator("first");
        var second = new ConfigCachingDecorator("second");

        // first has lower order, so it executes first (outer)
        var proxy = RegistryStorageProxyFactory.createProxy(raw, List.of(first, second));

        var result = proxy.getConfigProperty("test");
        // first decorator adds its tag, then calls delegate which is second,
        // second adds its tag, then calls delegate which is raw
        assertEquals("raw:second:first", result.getName());
    }

    @Test
    void exceptionTypesArePreserved() {
        var raw = new StubStorage() {
            @Override
            public String storageName() {
                throw new RegistryStorageException("test error");
            }
        };

        var proxy = RegistryStorageProxyFactory.createProxy(raw, List.of());
        assertThrows(RegistryStorageException.class, proxy::storageName);
    }

    @Test
    void emptyDecoratorListReturnsRawStorage() {
        var raw = new StubStorage();
        var result = RegistryStorageProxyFactory.createProxy(raw, List.of());
        // With no decorators, the factory returns rawStorage directly
        assertEquals(raw, result);
    }

    @Test
    void declaresMethodDetectsOverriddenMethods() throws NoSuchMethodException {
        Method isReadOnly = RegistryStorage.class.getMethod("isReadOnly");
        Method storageName = RegistryStorage.class.getMethod("storageName");

        assertTrue(RegistryStorageProxyFactory.declaresMethod(TestDecorator.class, isReadOnly));
        assertFalse(RegistryStorageProxyFactory.declaresMethod(TestDecorator.class, storageName));
    }

    /**
     * A minimal decorator that only overrides isReadOnly().
     */
    static class TestDecorator extends RegistryStorageDecoratorBase {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public int order() {
            return 10;
        }

        public boolean isReadOnly() {
            return true;
        }
    }

    /**
     * A decorator that wraps getConfigProperty to tag the result.
     */
    static class ConfigCachingDecorator extends RegistryStorageDecoratorBase {
        private final String tag;

        ConfigCachingDecorator(String tag) {
            this.tag = tag;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public int order() {
            return "first".equals(tag) ? 10 : 20;
        }

        public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
            DynamicConfigPropertyDto result = delegate.getConfigProperty(propertyName);
            if (result != null) {
                result.setName(result.getName() + ":" + tag);
            }
            return result;
        }
    }

    /**
     * A stub that provides minimal implementations for the methods we test.
     */
    static class StubStorage extends AbstractStubStorage {
        int storageNameCallCount = 0;
        int isReadOnlyCallCount = 0;
        String configValue = "default";

        @Override
        public String storageName() {
            storageNameCallCount++;
            return "stub";
        }

        @Override
        public boolean isReadOnly() {
            isReadOnlyCallCount++;
            return false;
        }

        @Override
        public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
            var dto = new DynamicConfigPropertyDto();
            dto.setName(configValue);
            return dto;
        }
    }
}
