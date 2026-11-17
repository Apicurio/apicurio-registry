package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingStrategyTest {

    private final DefaultArtifactTypeUtilProviderImpl factory = new DefaultArtifactTypeUtilProviderImpl(true);
    private final ArtifactCoordinates parent = new ArtifactCoordinates("g", "root");

    private IndexedSource avro() {
        return new IndexedSource(InMemorySchemaSource.of("Exchange.avsc", "{}", "application/json"),
                ArtifactType.AVRO, "com.kt.common.Exchange", null);
    }

    private IndexedSource json() {
        return new IndexedSource(InMemorySchemaSource.of("common/foo.json", "{}", "application/json"),
                ArtifactType.JSON, null, null);
    }

    @Test
    void legacyUseAvroNamespaceFromAvroParent() {
        LegacyNamingStrategy s = new LegacyNamingStrategy(LegacyNamingStrategy.AvroMode.USE_AVRO_NAMESPACE,
                factory);
        assertEquals(new ArtifactCoordinates("com.kt.common", "Exchange"),
                s.name(new NamingContext(avro(), "com.kt.common.Exchange", ArtifactType.AVRO, parent)));
    }

    @Test
    void legacyInheritParentGroupUsesFullName() {
        LegacyNamingStrategy s = new LegacyNamingStrategy(LegacyNamingStrategy.AvroMode.INHERIT_PARENT_GROUP,
                factory);
        assertEquals(new ArtifactCoordinates("g", "com.kt.common.Exchange"),
                s.name(new NamingContext(avro(), "./avro/Exchange.avsc", ArtifactType.ASYNCAPI, parent)));
    }

    @Test
    void legacyKeepsRawRelativePathForJson() {
        LegacyNamingStrategy s = new LegacyNamingStrategy(LegacyNamingStrategy.AvroMode.INHERIT_PARENT_GROUP,
                factory);
        assertEquals(new ArtifactCoordinates("g", "./common/foo.json"),
                s.name(new NamingContext(json(), "./common/foo.json", ArtifactType.JSON, parent)));
    }

    @Test
    void defaultNormalizesLocationAndUsesAvroNamespace() {
        assertEquals(new ArtifactCoordinates("g", "common/foo.json"), DefaultNamingStrategy.INSTANCE
                .name(new NamingContext(json(), "./common/foo.json", ArtifactType.JSON, parent)));
        assertEquals(new ArtifactCoordinates("com.kt.common", "Exchange"), DefaultNamingStrategy.INSTANCE
                .name(new NamingContext(avro(), "x", ArtifactType.ASYNCAPI, parent)));
    }

    @Test
    void defaultAvroWithoutNamespaceUsesParentGroup() {
        IndexedSource noNs = new IndexedSource(InMemorySchemaSource.of("Exchange.avsc", "{}", "application/json"),
                ArtifactType.AVRO, "Exchange", null);
        assertEquals(new ArtifactCoordinates("g", "Exchange"), DefaultNamingStrategy.INSTANCE
                .name(new NamingContext(noNs, "Exchange", ArtifactType.AVRO, parent)));
    }
}
