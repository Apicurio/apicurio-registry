package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseSerdeTest {

    @SuppressWarnings("unchecked")
    private static <I> I noOpProxy(Class<I> type) {
        return (I) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> null);
    }

    @Test
    void configureFallsBackToDefaultIdHandlerWhenConfiguredAsNull() {
        SchemaResolver<Object, Object> resolver = noOpProxy(SchemaResolver.class);
        SchemaParser<Object, Object> parser = noOpProxy(SchemaParser.class);
        BaseSerde<Object, Object> serde = new BaseSerde<>(resolver);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SerdeConfig.ID_HANDLER, null);
        serde.configure(new SerdeConfig(configs), false, parser);

        assertEquals(Default4ByteIdHandler.class, serde.getIdHandler().getClass());
    }
}
