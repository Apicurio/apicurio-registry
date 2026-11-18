package io.apicurio.registry.ccompat.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.inject.Singleton;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class CCompatJacksonCustomizer implements JsonMapperBuilderCustomizer {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract static class NonNullMixin {
    }

    @Override
    public void customize(JsonMapper.Builder mapper) {
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v7.beans.Schema.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v7.beans.SchemaId.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v8.beans.Schema.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v8.beans.SchemaId.class, NonNullMixin.class);
    }
}
