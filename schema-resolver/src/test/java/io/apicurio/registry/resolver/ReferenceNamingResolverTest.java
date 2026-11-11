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
        // Avro-style names: the namespace strategy strips the namespace prefix only for AVRO.
        MockSchemaParser parser = new MockSchemaParser(schemas()).withArtifactType("AVRO")
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
        resolver.setArtifactResolverStrategy(fixedTopLevel("Order"));
        resolver.resolveSchema(new MockRecord<>("payload", null));
        return facade;
    }

    private static ArtifactReferenceResolverStrategy<String, String> fixedTopLevel(String artifactId) {
        return new ArtifactReferenceResolverStrategy<>() {
            @Override
            public ArtifactReference artifactReference(Record<String> data, ParsedSchema<String> ps) {
                return ArtifactReference.builder().groupId("orders").artifactId(artifactId).build();
            }

            @Override
            public boolean loadSchema() {
                return false;
            }
        };
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
    void strategyReceivesReferenceContext() {
        List<String> seen = new ArrayList<>();
        ReferenceNamingStrategy recording = ctx -> {
            seen.add(ctx.referenceName() + " default=" + ctx.defaultGroupId() + " ns=" + ctx.namespace()
                    + " type=" + ctx.artifactType() + " schema=" + ctx.schema().getParsedSchema());
            return new ReferenceCoordinates("refs", ctx.referenceName());
        };
        resolve(recording);
        assertEquals(List.of(
                "com.acme.Address default=orders ns=com.acme type=AVRO schema=address",
                "com.acme.geo.Point default=orders ns=com.acme.geo type=AVRO schema=point"),
                seen);
    }

    @Test
    void sameReferencedContentUnderDifferentCoordinatesIsRegisteredForEach() {
        // Not deterministic on purpose: the same Point content is named differently on each call (#11053).
        int[] calls = {0};
        ReferenceNamingStrategy alternating = ctx -> new ReferenceCoordinates("refs",
                ctx.referenceName() + "-" + calls[0]++);
        MockRegistryClientFacade facade = new MockRegistryClientFacade("x");
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(facade);
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.REFERENCE_NAMING_STRATEGY, alternating);
        resolver.configure(configs, new MockSchemaParser(schemas()).withArtifactType("AVRO"));
        resolver.setArtifactResolverStrategy(fixedTopLevel("Order"));
        resolver.resolveSchema(new MockRecord<>("payload", null));
        resolver.setArtifactResolverStrategy(fixedTopLevel("Order2"));
        resolver.resolveSchema(new MockRecord<>("payload", null));

        // Order is deliberate: each parent is named before its references, and references are registered first.
        assertEquals(List.of("refs/com.acme.geo.Point-1", "refs/com.acme.Address-0", "orders/Order",
                "refs/com.acme.geo.Point-3", "refs/com.acme.Address-2", "orders/Order2"),
                new ArrayList<>(facade.getCreated().keySet()));
    }

    @Test
    void unconfiguredResolverUsesTheDefaultStrategy() {
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        assertEquals(DefaultReferenceNamingStrategy.class, resolver.referenceNamingStrategy.getClass());
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
