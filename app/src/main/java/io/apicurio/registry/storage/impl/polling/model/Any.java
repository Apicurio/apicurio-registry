package io.apicurio.registry.storage.impl.polling.model;

import com.fasterxml.jackson.core.JsonProcessingException;
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
     * if its data format version can't be read. A file can have neither, for example a schema file.
     */
    public record Parsed(Optional<Any> any, Optional<String> formatError) {

        static Parsed empty() {
            return new Parsed(Optional.empty(), Optional.empty());
        }
    }

    public static Optional<Any> from(ProcessingState state, String path, ContentHandle content) {
        return parse(state, path, content).any();
    }

    /**
     * Parses a file. Parse errors are recorded in {@code state}, if given. A file in a data format
     * version that this registry doesn't read is returned with a format error, which the caller
     * reports when the file is loaded, so that the load fails instead of skipping the file.
     */
    public static Parsed parse(ProcessingState state, String path, ContentHandle content) {
        try {
            var raw = YAML_MAPPER.readTree(content.bytes());
            var typeNode = raw == null ? null : raw.get(DataFormat.TYPE_FIELD);
            if (typeNode == null || typeNode.textValue() == null) {
                return Parsed.empty();
            }
            var rawType = typeNode.textValue();
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
                    return new Parsed(Optional.empty(), resolution.error(path));
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
                return new Parsed(Optional.of(any), Optional.empty());
            } catch (JsonProcessingException ex) {
                if (state != null) {
                    state.recordError("Could not parse file %s as %s: %s", path, type, ex.getOriginalMessage());
                }
                return Parsed.empty();
            }
        } catch (IOException ex) {
            // Only report parse errors for files that look like registry metadata files
            if (isMetadataFile(state, path)) {
                if (state != null) {
                    state.recordError("Could not parse file %s: %s", path, ex.getMessage());
                }
            }
            return Parsed.empty();
        }
    }

    private static boolean isMetadataFile(ProcessingState state, String path) {
        if (state == null || state.getConfig() == null) {
            return false;
        }
        return state.getConfig().isMetadataFile(path);
    }
}
