package io.apicurio.registry.ccompat.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class CCompatJacksonCustomizer implements ObjectMapperCustomizer {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract static class NonNullMixin {
    }

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v7.beans.Schema.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v7.beans.SchemaId.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v8.beans.Schema.class, NonNullMixin.class);
        mapper.addMixIn(io.apicurio.registry.ccompat.rest.v8.beans.SchemaId.class, NonNullMixin.class);
    }
}
