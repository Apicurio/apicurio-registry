package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.strategy.DefaultReferenceNamingStrategy;
import io.apicurio.registry.resolver.strategy.NamespaceGroupReferenceNamingStrategy;
import io.apicurio.registry.resolver.strategy.ReferenceCoordinates;
import io.apicurio.registry.resolver.strategy.ReferenceNamingStrategy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReferenceNamingResolverTest {

    /** Order (top) -> com.acme.Address -> com.acme.geo.Point. */
    private static ParsedSchemaImpl<String> schemas() {
        ParsedSchemaImpl<String> point = schema("com.acme.geo.Point", "point", List.of());
        ParsedSchemaImpl<String> address = schema("com.acme.Address", "address", List.of(point));
        return schema("com.acme.Order", "order", List.of(address));
    }

    private static ParsedSchemaImpl<String> schema(String name, String content, List<ParsedSchema<String>> refs) {
        ParsedSchemaImpl<String> s = new ParsedSchemaImpl<>();
        s.setParsedSchema(content);
        s.setRawSchema(content.getBytes(StandardCharsets.UTF_8));
        s.setReferenceName(name);
        s.setSchemaReferences(refs);
        return s;
    }

    private static MockRegistryClientFacade resolve(Object strategy) {
        MockRegistryClientFacade facade = new MockRegistryClientFacade("x");
        MockSchemaParser parser = new MockSchemaParser(schemas())
                .withNamespace("com.acme.Address", "com.acme")
                .withNamespace("com.acme.geo.Point", "com.acme.geo");
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(facade);
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        if (strategy != null) {
            configs.put(SchemaResolverConfig.REFERENCE_NAMING_STRATEGY, strategy);
        }
        resolver.configure(configs, parser);
        resolver.setArtifactResolverStrategy(new ArtifactReferenceResolverStrategy<>() {
            @Override
            public ArtifactReference artifactReference(Record<String> data, ParsedSchema<String> ps) {
                return ArtifactReference.builder().groupId("orders").artifactId("Order").build();
            }

            @Override
            public boolean loadSchema() {
                return false;
            }
        });
        resolver.resolveSchema(new MockRecord<>("payload", null));
        return facade;
    }

    private static RegistryArtifactReference ref(String name, String group, String artifactId) {
        return RegistryArtifactReference.builder().name(name).groupId(group).artifactId(artifactId).version("1")
                .build();
    }

    @Test
    void defaultStrategyKeepsTodaysCoordinates() {
        MockRegistryClientFacade facade = resolve(null);
        assertEquals(List.of("orders/com.acme.geo.Point", "orders/com.acme.Address", "orders/Order"),
                new ArrayList<>(facade.getCreated().keySet()));
        assertEquals(Set.of(ref("com.acme.geo.Point", "orders", "com.acme.geo.Point")),
                facade.getCreated().get("orders/com.acme.Address"));
        assertEquals(Set.of(ref("com.acme.Address", "orders", "com.acme.Address")),
                facade.getCreated().get("orders/Order"));
    }

    @Test
    void namespaceStrategyByClassNameRegistersInNamespaceGroups() {
        MockRegistryClientFacade facade = resolve(NamespaceGroupReferenceNamingStrategy.class.getName());
        assertEquals(List.of("com.acme.geo/Point", "com.acme/Address", "orders/Order"),
                new ArrayList<>(facade.getCreated().keySet()));
        // The reference names are unchanged; only the coordinates move.
        assertEquals(Set.of(ref("com.acme.geo.Point", "com.acme.geo", "Point")),
                facade.getCreated().get("com.acme/Address"));
        assertEquals(Set.of(ref("com.acme.Address", "com.acme", "Address")),
                facade.getCreated().get("orders/Order"));
    }

    @Test
    void blankGroupFromStrategyMeansDefaultGroup() {
        ReferenceNamingStrategy blank = ctx -> new ReferenceCoordinates("  ", ctx.referenceName());
        MockRegistryClientFacade facade = resolve(blank);
        assertEquals(List.of("default/com.acme.geo.Point", "default/com.acme.Address", "orders/Order"),
                new ArrayList<>(facade.getCreated().keySet()));
    }

    @Test
    void strategyReceivesDirectParentCoordinates() {
        List<String> seen = new ArrayList<>();
        ReferenceNamingStrategy recording = ctx -> {
            seen.add(ctx.referenceName() + " parent=" + ctx.parentGroupId() + "/" + ctx.parentArtifactId()
                    + " default=" + ctx.defaultGroupId() + " ns=" + ctx.namespace() + " type="
                    + ctx.artifactType());
            return new ReferenceCoordinates("refs", ctx.referenceName());
        };
        resolve(recording);
        assertEquals(List.of(
                "com.acme.Address parent=orders/Order default=orders ns=com.acme type=Mock",
                "com.acme.geo.Point parent=refs/com.acme.Address default=orders ns=com.acme.geo type=Mock"),
                seen);
    }

    @Test
    void strategyConfiguredByClassAndDefaultIsTheDefaultStrategy() {
        assertEquals(DefaultReferenceNamingStrategy.class.getName(),
                new SchemaResolverConfig(Map.of()).getReferenceNamingStrategy());
        MockRegistryClientFacade facade = resolve(NamespaceGroupReferenceNamingStrategy.class);
        assertEquals(List.of("com.acme.geo/Point", "com.acme/Address", "orders/Order"),
                new ArrayList<>(facade.getCreated().keySet()));
    }

    @Test
    void blankArtifactIdFailsClearly() {
        ReferenceNamingStrategy blank = ctx -> new ReferenceCoordinates("g", " ");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> resolve(blank));
        assertEquals("Reference naming strategy " + blank.getClass().getName()
                + " returned no artifact ID for reference 'com.acme.Address'.", e.getMessage());
    }

    @Test
    void nullResultFailsClearly() {
        ReferenceNamingStrategy none = ctx -> null;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> resolve(none));
        assertEquals("Reference naming strategy " + none.getClass().getName()
                + " returned no artifact ID for reference 'com.acme.Address'.", e.getMessage());
    }
}
