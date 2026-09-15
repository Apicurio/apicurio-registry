package io.apicurio.registry.mcpregistry;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;

/** Parses the MCP Registry's RFC 3339 incremental-sync query parameter. */
@Provider
public class McpRegistryDateParamConverterProvider implements ParamConverterProvider {

    @Override
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != Date.class) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof QueryParam queryParam && "updated_since".equals(queryParam.value())) {
                return new ParamConverter<T>() {
                    @Override
                    public T fromString(String value) {
                        if (value == null) {
                            return null;
                        }
                        try {
                            return rawType.cast(Date.from(Instant.parse(value)));
                        } catch (DateTimeParseException | IllegalArgumentException e) {
                            throw new BadRequestException("'updated_since' must be an RFC 3339 timestamp");
                        }
                    }

                    @Override
                    public String toString(T value) {
                        return ((Date) value).toInstant().toString();
                    }
                };
            }
        }
        return null;
    }
}
