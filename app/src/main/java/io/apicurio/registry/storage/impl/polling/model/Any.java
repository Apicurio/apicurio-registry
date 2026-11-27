package io.apicurio.registry.storage.impl.polling.model;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import io.apicurio.registry.storage.impl.polling.format.DataFormat;
import io.apicurio.registry.storage.impl.polling.format.FormatVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;
import static lombok.AccessLevel.PRIVATE;

@Builder
@AllArgsConstructor(access = PRIVATE)
@Getter
@EqualsAndHashCode
@ToString
public class Any {

    private static final Logger log = LoggerFactory.getLogger(Any.class);

    /**
     * The file as written, before any upgrade to the current data format version.
     */
    private JsonNode raw;

    private Type type;

    /**
     * The data format version the file was written in.
     */
    private FormatVersion formatVersion;

    /**
     * The entity, bound from the file after it was upgraded to the current data format version.
     */
    private Object entity;

    /**
     * The result of parsing a file: the entity, if the file is a readable metadata file, and an error,
     * if its data format version can't be read, or if a metadata file fails to parse. A file can have
     * neither, for example a schema file.
     */
    public record Parsed(Optional<Any> any, Optional<String> formatError, Optional<String> parseError) {

        static Parsed empty() {
            return new Parsed(Optional.empty(), Optional.empty(), Optional.empty());
        }

        static Parsed error(String message) {
            return new Parsed(Optional.empty(), Optional.empty(), Optional.of(message));
        }
    }

    /**
     * Parses a file. A file in a data format version that this registry doesn't read, a metadata file
     * that fails to parse, or a metadata file with no usable {@code $type}, is returned with an error,
     * which the caller reports when the file is loaded, so that the load fails instead of silently
     * skipping the file. {@code state} is used only to look up the configured file suffixes; errors
     * are not recorded on it directly, since the caller may parse with a disposable state before the
     * file joins the real one (GitOps and KubernetesOps both do this), and such errors would be lost.
     */
    public static Parsed parse(ProcessingState state, String path, ContentHandle content) {
        try {
            var raw = YAML_MAPPER.readTree(content.bytes());
            var typeNode = raw == null ? null : raw.get(DataFormat.TYPE_FIELD);
            if (typeNode == null || typeNode.textValue() == null) {
                if (raw != null && raw.has(DataFormat.TYPE_FIELD) && isMetadataFile(state, path)) {
                    // Has a $type field, but it's not a usable string (null, a number, an object, ...)
                    return Parsed.error("File " + path + " has a $type field that is not a string.");
                }
                if (isMetadataFile(state, path)) {
                    // No $type field at all: not failed, since a metadata-suffix file might
                    // legitimately not be a registry file, but worth naming so it isn't missed.
                    log.warn("File {} has no $type field. It will not be loaded as registry metadata.",
                            path);
                }
                return Parsed.empty();
            }
            var rawType = typeNode.textValue();
            // Only resolve the $type, and possibly fail the load over it, when the path is known,
            // from the configured file suffixes, to be a registry metadata file. A content file (a
            // schema, for example) may happen to have a top-level "$type" field of its own, readable
            // or not; this matters for KubernetesOps, whose ConfigMapDataFile parses every entry
            // unconditionally, unlike GitOps, which only parses files that already look like metadata
            // files by their path.
            if (isKnownNonMetadataFile(state, path)) {
                return Parsed.empty();
            }
            var resolution = DataFormat.resolve(rawType);
            switch (resolution.outcome()) {
                case UNKNOWN -> {
                    // A $type that is not a registry type, or a typo
                    if (isMetadataFile(state, path)) {
                        log.warn("File {} has unrecognized $type '{}'. It may contain a typo.", path, rawType);
                    }
                    return Parsed.empty();
                }
                case REMOVED, NEWER -> {
                    return new Parsed(Optional.empty(), resolution.error(path), Optional.empty());
                }
                default -> {
                    // READABLE, handled below
                }
            }
            var type = resolution.kind();
            try {
                // Only an object has a $type field, so raw is an ObjectNode here
                var upgraded = DataFormat.upgrade(((ObjectNode) raw).deepCopy(), type, resolution.version());
                var entity = YAML_MAPPER.treeToValue(upgraded, type.getKlass());
                var any = Any.builder().raw(raw).type(type).formatVersion(resolution.version())
                        .entity(entity).build();
                return new Parsed(Optional.of(any), Optional.empty(), Optional.empty());
            } catch (JsonProcessingException ex) {
                // The path guard above already excludes a non-metadata file, so reaching here means
                // this is a known registry metadata file whose content doesn't bind.
                // ex.getOriginalMessage() names the internal model class (e.g. "class
                // io...model.v0.Artifact"); log the full diagnostic, but keep internal class names
                // out of the error that reaches API clients (GitOpsStatus, dry-run validation).
                log.warn("File {} could not bind as {}: {}", path, type, ex.getOriginalMessage(), ex);
                return Parsed.error(
                        "Could not parse file " + path + " as " + type + ": " + sanitizedMessage(ex));
            }
        } catch (IOException ex) {
            // Only report parse errors for files that look like registry metadata files. A content
            // file (a binary schema, for example) is not valid YAML/JSON at all, and that's expected.
            if (isMetadataFile(state, path)) {
                // ex.getMessage() names the offending line and, for a syntax error, dumps the whole
                // file (Source: ...); log the full diagnostic, but keep both out of the error that
                // reaches API clients (GitOpsStatus, dry-run validation).
                log.warn("Could not parse file {}: {}", path, ex.getMessage(), ex);
                var location = ex instanceof JsonProcessingException jpe ? jpe.getLocation() : null;
                return Parsed.error("Could not parse file " + path + ": invalid content"
                        + locationSuffix(location));
            }
            return Parsed.empty();
        }
    }

    /**
     * A message safe to return to API clients: the location, and the offending field name when known,
     * without the exception's own message, which names the internal model class.
     */
    private static String sanitizedMessage(JsonProcessingException ex) {
        String field = null;
        if (ex instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            field = jme.getPath().get(jme.getPath().size() - 1).getFieldName();
        }
        var description = field != null ? "unrecognized or invalid field '" + field + "'" : "invalid content";
        return description + locationSuffix(ex.getLocation());
    }

    /**
     * " at line X, column Y", only when both coordinates are known (greater than zero). Binding
     * through a tree already parsed into memory, for example, has no location: Jackson reports
     * {@code JsonLocation.NA}, whose line and column are both {@code -1}.
     */
    private static String locationSuffix(JsonLocation location) {
        if (location != null && location.getLineNr() > 0 && location.getColumnNr() > 0) {
            return " at line " + location.getLineNr() + ", column " + location.getColumnNr();
        }
        return "";
    }

    private static boolean isMetadataFile(ProcessingState state, String path) {
        if (state == null || state.getConfig() == null) {
            return false;
        }
        return state.getConfig().isMetadataFile(path);
    }

    /**
     * True only when the path is positively known, from the configured file suffixes, not to be a
     * registry metadata file. Without a config to check against, this is false (conservative): the
     * caller treats the path as a metadata file, as before this check existed.
     */
    private static boolean isKnownNonMetadataFile(ProcessingState state, String path) {
        return state != null && state.getConfig() != null && !state.getConfig().isMetadataFile(path);
    }
}
