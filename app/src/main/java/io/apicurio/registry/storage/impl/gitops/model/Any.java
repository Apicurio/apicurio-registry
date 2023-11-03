package io.apicurio.registry.storage.impl.gitops.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.impl.gitops.ProcessingState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static io.apicurio.registry.storage.impl.gitops.YAMLObjectMapper.MAPPER;
import static lombok.AccessLevel.PRIVATE;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
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
                        var any = Any.builder()
                                .raw(raw)
                                .type(type.get())
                                .entity(entity)
                                .build();
                        return Optional.of(any);
                    } catch (JsonProcessingException ex) {
                        state.recordError("Could not parse file %s as %s: %s",
                                path, type.get(), ex.getOriginalMessage());
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }
}
