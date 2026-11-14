package io.apicurio.registry.rest.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Produces a Jackson 2 {@link ObjectMapper} bean.
 * <p>
 * Quarkus 4 moved to Jackson 3 and only produces a {@code tools.jackson.databind.json.JsonMapper} bean.
 * Code that still injects the Jackson 2 {@code ObjectMapper} (for example to read and write persisted
 * Iceberg metadata and MCP server definitions) gets this mapper instead. It is configured like the mapper
 * Quarkus 3 produced, so the serialized form of that data does not change.
 * <p>
 * Temporary bridge: remove it once the injection points are migrated to Jackson 3.
 */
@Singleton
public class LegacyJacksonObjectMapperProducer {

    @Inject
    JacksonDateTimeCustomizer dateTimeCustomizer;

    @Produces
    @Singleton
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new ParameterNamesModule());
        // Quarkus 3 defaults (quarkus.jackson.*)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
        dateTimeCustomizer.customize(mapper);
        return mapper;
    }
}
