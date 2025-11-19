package io.apicurio.registry.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ObjectMapperCustomizerImpl implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.registerModule(new JavaTimeModule());

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Fix for MCP SDK Zod validation: Don't serialize null fields (especially _meta)
        // The MCP TypeScript SDK expects _meta to be either omitted or an object, never null
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        mapper.setMixInResolver(new ClassIntrospector.MixInResolver() {
            @Override
            public Class<?> findMixInClassFor(Class<?> cls) {
                return Mixin.class;
            }

            @Override
            public ClassIntrospector.MixInResolver copy() {
                return this;
            }
        });

    }

    @JsonIgnoreProperties({"fieldDeserializers"})
    public abstract class Mixin {
    }
}
