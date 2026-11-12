package io.apicurio.registry.storage.impl.polling.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.format.DataFormat.Outcome;
import io.apicurio.registry.storage.impl.polling.model.Any;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataFormatTest {

    @Test
    void currentVersionIsV1AndV0IsDeprecated() {
        assertSame(FormatVersion.V1, FormatVersion.current());
        assertSame(FormatVersion.Status.DEPRECATED, FormatVersion.V0.status());
        assertEquals("3.4.0", FormatVersion.V0.deprecatedIn());
        assertEquals("artifact-v1", Type.ARTIFACT.getType());
        assertEquals("content-v1", DataFormat.currentTypeName(Type.CONTENT));
    }

    /**
     * ADR-0013: at most two versions are readable, one of them current.
     */
    @Test
    void atMostTwoReadableVersionsAndOneCurrent() {
        assertTrue(Arrays.stream(FormatVersion.values()).filter(FormatVersion::isReadable).count() <= 2);
        assertEquals(1, Arrays.stream(FormatVersion.values())
                .filter(v -> v.status() == FormatVersion.Status.CURRENT).count());
    }

    /**
     * Every readable version must reach the current one through the migrations, one version at a time.
     */
    @Test
    void everyReadableVersionHasAMigrationChainToCurrent() {
        for (FormatMigration migration : DataFormat.migrations()) {
            assertEquals(migration.from().number() + 1, migration.to().number(), migration.getClass().getName());
        }
        for (FormatVersion version : FormatVersion.values()) {
            if (version.isReadable() && version != FormatVersion.current()) {
                var file = YAML_MAPPER.createObjectNode().put("$type", "registry-" + version.id());
                var upgraded = DataFormat.upgrade(file, Type.REGISTRY, version);
                assertEquals("registry-v1", upgraded.get("$type").textValue());
            }
        }
    }

    @Test
    void resolvesEveryKindInBothReadableVersions() {
        for (Type kind : Type.values()) {
            for (FormatVersion version : new FormatVersion[] { FormatVersion.V0, FormatVersion.V1 }) {
                var resolution = DataFormat.resolve(kind.getKind() + "-" + version.id());
                assertEquals(Outcome.READABLE, resolution.outcome());
                assertSame(kind, resolution.kind());
                assertSame(version, resolution.version());
                assertEquals(Optional.empty(), resolution.error("x.registry.yaml"));
            }
        }
    }

    @Test
    void newerVersionOfAKnownKindIsAnError() {
        var resolution = DataFormat.resolve("artifact-v2");
        assertEquals(Outcome.NEWER, resolution.outcome());
        assertSame(Type.ARTIFACT, resolution.kind());
        assertEquals(Optional.of("File orders/order.registry.yaml uses $type artifact-v2, a newer GitOps data format"
                + " version than this registry reads (the current version is v1). It requires a newer registry"
                + " release."), resolution.error("orders/order.registry.yaml"));
    }

    @Test
    void removedVersionErrorNamesTheCurrentType() {
        // No version is removed yet, so build the resolution that a removed v0 would produce.
        var resolution = new DataFormat.Resolution(Outcome.REMOVED, "group-v0", Type.GROUP, FormatVersion.V0);
        var error = resolution.error("g.registry.yaml").orElseThrow();
        assertTrue(error.startsWith("File g.registry.yaml uses the GitOps data format version v0 ($type: group-v0),"
                + " which was removed in "), error);
        assertTrue(error.endsWith(" Convert it to version v1 ($type: group-v1)."), error);
    }

    @Test
    void unknownTypesAreNotRegistryFiles() {
        for (String rawType : new String[] { "foo-v1", "artifact", "artifact-v", "artifact-v01", "Artifact-v1",
                "artifact-v1 ", "artifact-vX", "" }) {
            var resolution = DataFormat.resolve(rawType);
            assertEquals(Outcome.UNKNOWN, resolution.outcome(), rawType);
            assertNull(resolution.kind(), rawType);
            assertEquals(Optional.empty(), resolution.error("x"), rawType);
        }
        assertEquals(Outcome.UNKNOWN, DataFormat.resolve(null).outcome());
    }

    @Test
    void upgradeOfACurrentFileLeavesItUnchanged() {
        ObjectNode file = YAML_MAPPER.createObjectNode().put("$type", "group-v1").put("groupId", "g");
        var copy = file.deepCopy();
        assertEquals(copy, DataFormat.upgrade(file, Type.GROUP, FormatVersion.V1));
    }

    @Test
    void v0AndV1FilesParseToTheSameEntity() {
        var v0 = Any.parse(null, "a.registry.yaml", ContentHandle.create(
                "$type: artifact-v0\ngroupId: g\nartifactId: a\nversions: []\n"));
        var v1 = Any.parse(null, "a.registry.yaml", ContentHandle.create(
                "$type: artifact-v1\ngroupId: g\nartifactId: a\nversions: []\n"));

        assertSame(FormatVersion.V0, v0.any().orElseThrow().getFormatVersion());
        assertSame(FormatVersion.V1, v1.any().orElseThrow().getFormatVersion());
        assertSame(Type.ARTIFACT, v0.any().orElseThrow().getType());
        Artifact a0 = (Artifact) v0.any().orElseThrow().getEntity();
        Artifact a1 = (Artifact) v1.any().orElseThrow().getEntity();
        assertEquals(a1, a0);
        assertEquals("a", a0.getArtifactId());
        // The raw tree is the file as written
        assertEquals("artifact-v0", v0.any().orElseThrow().getRaw().get("$type").textValue());
    }

    @Test
    void newerFileParsesToAFormatErrorAndNoEntity() {
        var parsed = Any.parse(null, "a.registry.yaml", ContentHandle.create(
                "$type: artifact-v2\ngroupId: g\nartifactId: a\n"));
        assertEquals(Optional.empty(), parsed.any());
        assertTrue(parsed.formatError().orElseThrow().contains("uses $type artifact-v2"),
                parsed.formatError().orElseThrow());
    }
}
