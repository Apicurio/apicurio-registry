package io.apicurio.registry.storage.impl.polling.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.PollingStorageConfig;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import io.apicurio.registry.storage.impl.polling.format.DataFormat.Outcome;
import io.apicurio.registry.storage.impl.polling.model.Any;
import io.apicurio.registry.storage.impl.polling.model.Type;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * ADR-0029: at most two versions are readable, one of them current.
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

    /**
     * KubernetesOps' ConfigMapDataFile parses every ConfigMap entry unconditionally, unlike GitOps,
     * which only parses files whose path already looks like a registry metadata file. So a content
     * file (a schema, say) that happens to have a top-level "$type" field of its own must not fail
     * the load just because the value looks like a newer or removed format version.
     */
    @Test
    void newerOrRemovedTypeInANonMetadataFileDoesNotFailTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "content/Person.json",
                ContentHandle.create("{\"$type\": \"artifact-v2\", \"type\": \"object\"}"));
        assertEquals(Optional.empty(), parsed.any());
        assertEquals(Optional.empty(), parsed.formatError());
    }

    /**
     * The same content, at a path that does look like a registry metadata file, still fails: only
     * the path decides, not whether the file happens to be reachable as "content".
     */
    @Test
    void newerOrRemovedTypeInAMetadataFileStillFailsTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "artifact-person.registry.json",
                ContentHandle.create("{\"$type\": \"artifact-v2\", \"groupId\": \"g\", \"artifactId\": \"a\"}"));
        assertEquals(Optional.empty(), parsed.any());
        assertTrue(parsed.formatError().orElseThrow().contains("uses $type artifact-v2"),
                parsed.formatError().orElseThrow());
    }

    /**
     * A content file (a schema, say) may happen to have a top-level "$type" field with a *readable*
     * value, not a removed or newer one, and that value may happen to bind cleanly to the kind's
     * model class. KubernetesOps' ConfigMapDataFile parses every ConfigMap entry unconditionally, so
     * without a path guard, such a file would be indexed as real registry metadata. Only a known
     * metadata file is ever resolved against $type at all.
     */
    @Test
    void readableAndBindableTypeInANonMetadataFileDoesNotFailTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "content/schema.json", ContentHandle.create(
                "{\"$type\": \"artifact-v1\", \"groupId\": \"missing\", \"artifactId\": \"example\"}"));
        assertEquals(Optional.empty(), parsed.any());
        assertEquals(Optional.empty(), parsed.formatError());
        assertEquals(Optional.empty(), parsed.parseError());
    }

    /**
     * The same kind of content file, but whose fields don't bind to that type's model class either.
     * The load must not fail: only a known registry metadata file does.
     */
    @Test
    void readableTypeThatFailsToBindInANonMetadataFileDoesNotFailTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "content/Person.json", ContentHandle.create(
                "{\"$type\": \"artifact-v1\", \"type\": \"object\", \"properties\": {\"name\": {}}}"));
        assertEquals(Optional.empty(), parsed.any());
        assertEquals(Optional.empty(), parsed.parseError());
    }

    /**
     * The same binding failure, at a path that does look like a registry metadata file, fails the
     * load, naming the file and the field, but not the internal model class (#11393 review).
     */
    @Test
    void readableTypeThatFailsToBindInAMetadataFileFailsWithASanitizedMessage() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "artifact-person.registry.json", ContentHandle.create(
                "{\"$type\": \"artifact-v1\", \"groupId\": \"g\", \"artifactId\": \"a\", \"nmae\": \"typo\"}"));
        assertEquals(Optional.empty(), parsed.any());
        var error = parsed.parseError().orElseThrow();
        assertTrue(error.contains("artifact-person.registry.json"), error);
        assertTrue(error.contains("nmae"), error);
        assertTrue(error.toLowerCase(Locale.ROOT).contains("artifact-v1"), error);
        assertFalse(error.contains("io.apicurio.registry"), error);
        assertFalse(error.contains("class "), error);
        // Binding happens on a tree already parsed into memory, with no location (JsonLocation.NA,
        // line -1 column -1): the message must not leak that placeholder.
        assertFalse(error.contains("-1"), error);
    }

    /**
     * A metadata file with a YAML/JSON syntax error fails the load, naming the file and its line and
     * column, but neither the offending file content nor Jackson's own message, which quotes the
     * whole file (#11393 review).
     */
    @Test
    void syntaxErrorInAMetadataFileFailsWithASanitizedMessage() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "artifact-person.registry.json",
                ContentHandle.create("{\"$type\": \"artifact-v1\", \"groupId\": [\"secret-value\"}"));
        assertEquals(Optional.empty(), parsed.any());
        var error = parsed.parseError().orElseThrow();
        assertTrue(error.contains("artifact-person.registry.json"), error);
        assertTrue(error.contains("line"), error);
        assertTrue(error.contains("column"), error);
        assertFalse(error.contains("secret-value"), error);
        assertFalse(error.contains("Source:"), error);
    }

    /**
     * A metadata-suffix file whose $type field is present but not a string (a number, an object, or
     * null) must fail the load, naming the file, rather than being silently skipped like a file with
     * no $type at all.
     */
    @Test
    void nonStringTypeFieldInAMetadataFileFailsTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        for (String badType : new String[] { "1", "{\"a\": \"b\"}", "null" }) {
            var parsed = Any.parse(state, "artifact-person.registry.json",
                    ContentHandle.create("{\"$type\": " + badType + ", \"groupId\": \"g\"}"));
            assertEquals(Optional.empty(), parsed.any(), badType);
            var error = parsed.parseError().orElseThrow(() -> new AssertionError("no error for " + badType));
            assertTrue(error.contains("artifact-person.registry.json"), error);
            assertTrue(error.contains("not a string"), error);
        }
    }

    /**
     * A metadata-suffix file with no $type field at all is not a parse error: it might legitimately
     * not be a registry file (#11111 proposal item 2 says a warning naming it is the minimum).
     */
    @Test
    void noTypeFieldInAMetadataFileDoesNotFailTheLoad() {
        var state = new ProcessingState(new TestPollingStorageConfig(), null);
        var parsed = Any.parse(state, "artifact-person.registry.json",
                ContentHandle.create("{\"groupId\": \"g\", \"artifactId\": \"a\"}"));
        assertEquals(Optional.empty(), parsed.any());
        assertEquals(Optional.empty(), parsed.parseError());
        assertEquals(Optional.empty(), parsed.formatError());
    }

    private static class TestPollingStorageConfig implements PollingStorageConfig {
        @Override
        public String getStorageName() {
            return "test";
        }

        @Override
        public String getRegistryId() {
            return "test";
        }

        @Override
        public boolean isDeterministicIdGenerationEnabled() {
            return true;
        }

        @Override
        public Duration getPollPeriod() {
            return Duration.ofSeconds(10);
        }

        @Override
        public Duration getDebounceQuietPeriod() {
            return Duration.ZERO;
        }

        @Override
        public Duration getDebounceMaxWaitPeriod() {
            return Duration.ZERO;
        }

        @Override
        public boolean isRequireRegistryConfig() {
            return false;
        }

        @Override
        public List<String> getFileSuffixes() {
            return List.of("registry");
        }

        @Override
        public String getSourceLabelKey() {
            return "";
        }
    }
}
