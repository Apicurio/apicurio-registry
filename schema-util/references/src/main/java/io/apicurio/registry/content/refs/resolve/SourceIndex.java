package io.apicurio.registry.content.refs.resolve;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ArtifactType;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Index of schema sources and existing artifacts used to resolve reference strings to targets.
 */
public final class SourceIndex {

    private final Map<SchemaSource, IndexedSource> bySource = new IdentityHashMap<>();
    private final Map<String, IndexedSource> byLocation = new HashMap<>();
    private final Map<String, IndexedSource> byAvroName = new HashMap<>();
    private final Map<String, IndexedSource> byJsonId = new HashMap<>();
    private final Map<String, ExistingArtifact> existingByName = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private SourceIndex() {
    }

    /**
     * Builds an index from the given sources and existing artifacts. Sources whose type cannot be
     * detected are skipped. When two sources declare the same Avro full name or JSON Schema {@code $id},
     * the first one (in source-set order) wins and a warning is recorded.
     *
     * @param sources the local schema sources
     * @param existing artifacts already present in the registry, keyed by reference name
     * @param detector the type detector
     * @return the index
     */
    public static SourceIndex build(SchemaSourceSet sources, List<ExistingArtifact> existing,
            TypeDetector detector) {
        SourceIndex index = new SourceIndex();
        for (ExistingArtifact artifact : existing) {
            index.existingByName.put(artifact.referenceName(), artifact);
        }
        for (SchemaSource source : sources.sources()) {
            Optional<String> type = detector.detect(source);
            if (type.isEmpty()) {
                continue;
            }
            JsonNode root = parse(source);
            IndexedSource indexed = new IndexedSource(source, type.get(), avroFullName(root),
                    jsonSchemaId(root));
            index.bySource.put(source, indexed);
            for (String alias : source.aliases()) {
                index.byLocation.putIfAbsent(SchemaSourceSet.normalize(alias), indexed);
            }
            if (indexed.avroFullName() != null && ArtifactType.AVRO.equals(indexed.artifactType())) {
                IndexedSource first = index.byAvroName.putIfAbsent(indexed.avroFullName(), indexed);
                if (first != null) {
                    index.warnings.add("Duplicate Avro name '" + indexed.avroFullName() + "' in "
                            + source.location() + "; using " + first.source().location());
                }
            }
            if (indexed.jsonSchemaId() != null) {
                IndexedSource first = index.byJsonId.putIfAbsent(indexed.jsonSchemaId(), indexed);
                if (first != null) {
                    index.warnings.add("Duplicate JSON Schema $id '" + indexed.jsonSchemaId() + "' in "
                            + source.location() + "; using " + first.source().location());
                }
            }
        }
        return index;
    }

    /**
     * Returns the non-fatal warnings recorded while building the index, in source-set order.
     *
     * @return an immutable list of warnings
     */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * Returns the indexed form of a source.
     *
     * @param source the source
     * @return the indexed source, or empty if the source was not indexed
     */
    public Optional<IndexedSource> bySource(SchemaSource source) {
        return Optional.ofNullable(bySource.get(source));
    }

    /**
     * Resolves a reference string found in {@code from}. Exactly one of source/existing is set; empty if
     * unresolved.
     *
     * @param resource the referenced resource string
     * @param from the referencing source
     * @return the lookup result, or empty if unresolved
     */
    public Optional<LookupResult> lookup(String resource, SchemaSource from) {
        if (resource == null) {
            return Optional.empty();
        }
        String key = stripFragment(resource);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        ExistingArtifact existing = existingByName.get(key);
        if (existing != null) {
            return Optional.of(new LookupResult(null, existing));
        }
        IndexedSource hit = byAvroName.get(key);
        if (hit == null) {
            hit = byJsonId.get(key);
        }
        if (hit == null && !isAbsoluteUri(key)) {
            hit = byLocation.get(SchemaSourceSet.normalize(parentOf(from.location()) + "/" + key));
        }
        if (hit == null) {
            IndexedSource rooted = byLocation.get(SchemaSourceSet.normalize(key));
            if (rooted != null && ArtifactType.PROTOBUF.equals(rooted.artifactType())) {
                hit = rooted;
            }
        }
        return Optional.ofNullable(hit).map(s -> new LookupResult(s, null));
    }

    private static String stripFragment(String value) {
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(0, hash) : value;
    }

    private static boolean isAbsoluteUri(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String parentOf(String location) {
        int slash = location.lastIndexOf('/');
        return slash >= 0 ? location.substring(0, slash) : "";
    }

    private static JsonNode parse(SchemaSource source) {
        try {
            return ContentTypeUtil.parseJsonOrYaml(source.content());
        } catch (Exception e) {
            return null;
        }
    }

    private static String avroFullName(JsonNode root) {
        try {
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode name = root.get("name");
            if (name == null || !name.isTextual() || name.asText().isBlank()) {
                return null;
            }
            JsonNode namespace = root.get("namespace");
            if (namespace == null || !namespace.isTextual() || namespace.asText().isBlank()) {
                return name.asText();
            }
            return namespace.asText() + "." + name.asText();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String jsonSchemaId(JsonNode root) {
        try {
            if (root == null || !root.isObject()) {
                return null;
            }
            JsonNode id = root.get("$id");
            if (id == null || !id.isTextual()) {
                return null;
            }
            String value = stripFragment(id.asText());
            return value.isEmpty() ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
