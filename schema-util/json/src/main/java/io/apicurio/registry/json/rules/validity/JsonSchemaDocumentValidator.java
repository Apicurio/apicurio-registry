package io.apicurio.registry.json.rules.validity;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import io.apicurio.registry.json.util.DataModelsJson;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apitomy.datamodels.DataModelsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Checks that a document is a well-formed JSON Schema, with the networknt validator: against the
 * meta-schema of its draft and, optionally, by loading it, which also catches what a meta-schema
 * can't, such as a {@code $ref} that doesn't resolve or a pattern that isn't a valid regular
 * expression.
 * <p>
 * Callers differ in three things, so each is configured: how the draft is chosen, including when
 * {@code $schema} is absent; what happens when {@code $schema} names no known draft; and whether the
 * schema is loaded.
 * <p>
 * Nothing is ever fetched. When loading, every document the schema refers to is requested from the
 * caller, which supplies its content.
 */
public final class JsonSchemaDocumentValidator {

    private static final String SCHEMA_KEYWORD = "$schema";

    /**
     * The base IRI of a schema without an absolute {@code $id}. A document it refers to by a relative
     * reference is requested from the caller by that relative reference, without this prefix.
     */
    private static final String BASE_IRI = "mem://input/";

    private static final SchemaRegistryConfig META_VALIDATION_CONFIG = SchemaRegistryConfig.builder()
            .pathType(PathType.JSON_POINTER).build();

    /**
     * Meta-schemas are immutable once built, so they are cached per draft. Each is built from a
     * document bundled in the validator library.
     */
    private static final Map<SpecificationVersion, Schema> META_SCHEMAS = new ConcurrentHashMap<>();

    /** Chooses the draft of a schema. */
    @FunctionalInterface
    public interface DraftDetector {

        /**
         * @return the draft the schema declares, or implies when it declares none; empty if its
         *         {@code $schema} names no draft this detector knows
         */
        Optional<SpecificationVersion> detect(JsonNode schema);
    }

    private final DraftDetector draftDetector;
    private final SpecificationVersion unrecognisedDraft;
    private final boolean loadSchema;

    private JsonSchemaDocumentValidator(Builder builder) {
        this.draftDetector = requireNonNull(builder.draftDetector, "draftDetector");
        this.unrecognisedDraft = builder.unrecognisedDraft;
        this.loadSchema = builder.loadSchema;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Apitomy Data Models' detector, which the JSON Schema compatibility checker uses too, so both
     * read a schema as the same draft. A schema without {@code $schema} is draft 7. A document the
     * detector reads as another kind of model, such as OpenAPI, has no known draft.
     */
    public static DraftDetector dataModelsDraftDetector() {
        return schema -> {
            if (!(schema instanceof ObjectNode)) {
                return Optional.of(SpecificationVersion.DRAFT_7);
            }
            try {
                return switch (DataModelsJson.discoverModelType(schema)) {
                    case JD4 -> Optional.of(SpecificationVersion.DRAFT_4);
                    case JD6 -> Optional.of(SpecificationVersion.DRAFT_6);
                    case JD7 -> Optional.of(SpecificationVersion.DRAFT_7);
                    case JM201909 -> Optional.of(SpecificationVersion.DRAFT_2019_09);
                    case JM202012 -> Optional.of(SpecificationVersion.DRAFT_2020_12);
                    default -> Optional.empty();
                };
            } catch (DataModelsException | IOException e) {
                return Optional.empty();
            }
        };
    }

    /**
     * The validator library's own detector. A schema without {@code $schema} is {@code whenAbsent}.
     * A {@code $schema} that is not a string names no known draft. The comparison ignores a missing
     * trailing {@code #}: {@code SpecificationVersion.fromDialectId} requires an exact match, but
     * draft identifiers up to 2019-09 are fragment-only URIs, so both forms name the same draft.
     */
    public static DraftDetector networkntDraftDetector(SpecificationVersion whenAbsent) {
        requireNonNull(whenAbsent, "whenAbsent");
        return schema -> {
            if (!schema.has(SCHEMA_KEYWORD)) {
                return Optional.of(whenAbsent);
            }
            JsonNode declared = schema.path(SCHEMA_KEYWORD);
            return declared.isString() ? fromDialectId(declared.asString()) : Optional.empty();
        };
    }

    /**
     * {@code SpecificationVersion.fromDialectId}, without requiring an exact match on a trailing
     * {@code #}: draft identifiers up to 2019-09 are fragment-only URIs, so both forms name the
     * same draft.
     */
    public static Optional<SpecificationVersion> fromDialectId(String dialectId) {
        for (SpecificationVersion version : SpecificationVersion.values()) {
            if (withoutTrailingHash(version.getDialectId()).equals(withoutTrailingHash(dialectId))) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }

    private static String withoutTrailingHash(String uri) {
        return uri.endsWith("#") ? uri.substring(0, uri.length() - 1) : uri;
    }

    /**
     * @return the draft the schema is validated as, or empty if its {@code $schema} names no known
     *         draft and that is reported rather than replaced
     */
    public Optional<SpecificationVersion> draftOf(JsonNode schema) {
        return draftDetector.detect(schema).or(() -> Optional.ofNullable(unrecognisedDraft));
    }

    /**
     * Validates a schema that refers to no other document, or one whose references don't need to
     * resolve because it isn't loaded.
     */
    public List<RuleViolation> validate(JsonNode schema) {
        return validate(schema, reference -> "{}");
    }

    /**
     * @param documents the content of each document the schema refers to, by its reference without
     *                  the fragment: as written when it is relative to the schema itself, otherwise
     *                  absolute. Only consulted when the schema is loaded.
     * @return each problem, at most one per location, with a JSON Pointer into the schema as its
     *         context; empty if the schema is valid
     */
    public List<RuleViolation> validate(JsonNode schema, Function<String, String> documents) {
        var draft = draftOf(schema);
        if (draft.isEmpty()) {
            return List.of(new RuleViolation("Unsupported JSON Schema dialect '"
                    + schema.path(SCHEMA_KEYWORD).asString() + "'", "/$schema"));
        }

        var violations = new ArrayList<RuleViolation>();
        var reportedLocations = new HashSet<String>();
        for (Error message : metaSchemaFor(draft.get()).validate(schema)) {
            var location = message.getInstanceLocation().toString();
            if (reportedLocations.add(location)) {
                violations.add(new RuleViolation(messageWithoutLocation(message, location), location));
            }
        }

        if (violations.isEmpty() && loadSchema) {
            load(schema, draft.get(), documents).ifPresent(violations::add);
        }
        return violations;
    }

    private static Schema metaSchemaFor(SpecificationVersion draft) {
        return META_SCHEMAS.computeIfAbsent(draft, version -> SchemaRegistry.withDefaultDialect(version,
                builder -> builder.schemaRegistryConfig(META_VALIDATION_CONFIG))
                .getSchema(SchemaLocation.of(version.getDialectId())));
    }

    /**
     * Builds the validators for the schema, which resolves its references and compiles its patterns.
     * The loader serves every document from {@code documents}, so the library's own loaders, which
     * would read the classpath or the network, are never reached.
     */
    private static Optional<RuleViolation> load(JsonNode schema, SpecificationVersion draft,
            Function<String, String> documents) {
        var registry = SchemaRegistry.withDefaultDialect(draft, builder -> builder
                .schemas((Function<String, String>) iri -> {
                    var content = documents.apply(iri.startsWith(BASE_IRI) ? iri.substring(BASE_IRI.length()) : iri);
                    return content != null ? content : "{}";
                }));

        // The draft was chosen above, possibly for a $schema the library doesn't know, so it is
        // declared explicitly rather than left for the library to look up.
        var root = schema;
        if (schema instanceof ObjectNode objectNode) {
            root = objectNode.deepCopy().put(SCHEMA_KEYWORD, draft.getDialectId());
        }

        try {
            registry.getSchema(SchemaLocation.of(BASE_IRI), root).initializeValidators();
            return Optional.empty();
        } catch (RuntimeException e) {
            var message = String.valueOf(e.getMessage());
            return Optional.of(new RuleViolation(message.startsWith(": ") ? message.substring(2) : message, ""));
        }
    }

    /**
     * The library prefixes every message with the instance location, which the violation already
     * carries as its context.
     */
    private static String messageWithoutLocation(Error message, String location) {
        var text = message.getMessage();
        var prefix = location + ": ";
        return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    }

    public static final class Builder {

        private DraftDetector draftDetector;
        private SpecificationVersion unrecognisedDraft;
        private boolean loadSchema;

        private Builder() {
        }

        public Builder draftDetector(DraftDetector draftDetector) {
            this.draftDetector = draftDetector;
            return this;
        }

        /**
         * Validates a schema whose {@code $schema} names no known draft as {@code draft}. By default
         * such a schema is reported as a single violation at {@code /$schema} instead.
         */
        public Builder treatUnrecognisedDraftAs(SpecificationVersion draft) {
            this.unrecognisedDraft = draft;
            return this;
        }

        /** Also load a schema that passes meta-schema validation. Off by default. */
        public Builder loadSchema(boolean loadSchema) {
            this.loadSchema = loadSchema;
            return this;
        }

        public JsonSchemaDocumentValidator build() {
            return new JsonSchemaDocumentValidator(this);
        }
    }
}
