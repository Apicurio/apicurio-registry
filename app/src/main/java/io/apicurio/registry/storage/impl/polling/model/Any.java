package io.apicurio.registry.storage.impl.polling.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.polling.ProcessingState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static io.apicurio.registry.util.YAMLObjectMapper.MAPPER;
import static lombok.AccessLevel.PRIVATE;

@Builder
@AllArgsConstructor(access = PRIVATE)
@Getter
@EqualsAndHashCode
@ToString
public class Any {

    private static final Logger log = LoggerFactory.getLogger(Any.class);

    private JsonNode raw;

    private Type type;

    private Object entity;

    public static Optional<Any> from(ProcessingState state, String path, ContentHandle content) {
        try {
            var raw = MAPPER.readTree(content.bytes());
            var typeNode = raw.get("$type");
            if (typeNode != null && typeNode.textValue() != null) {
                var rawType = typeNode.textValue();
                var type = Type.from(rawType);
                if (type.isPresent()) {
                    try {
                        var entity = MAPPER.treeToValue(raw, type.get().getKlass());
                        var any = Any.builder().raw(raw).type(type.get()).entity(entity).build();
                        return Optional.of(any);
                    } catch (JsonProcessingException ex) {
                        if (state != null) {
                            state.recordError("Could not parse file %s as %s: %s", path, type.get(),
                                    ex.getOriginalMessage());
                        }
                        return Optional.empty();
                    }
                } else {
                    // File has $type field but it's not a recognized type.
                    // This could be a newer format version or a typo.
                    if (isMetadataFile(state, path)) {
                        log.warn("File {} has unrecognized $type '{}'. "
                                + "It may be from a newer format version or contain a typo.", path, rawType);
                    }
                    return Optional.empty();
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            // Only report parse errors for files that look like registry metadata files
            if (isMetadataFile(state, path)) {
                if (state != null) {
                    state.recordError("Could not parse file %s: %s", path, ex.getMessage());
                }
            }
            return Optional.empty();
        }
    }

    private static boolean isMetadataFile(ProcessingState state, String path) {
        if (state == null || state.getConfig() == null) {
            return false;
        }
        return state.getConfig().isMetadataFile(path);
    }
}
