package io.apicurio.registry.rest;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.apicurio.registry.rest.v3.beans.ContractStatusTransition;
import io.apicurio.registry.services.http.CoreRegistryExceptionMapperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Intentionally handles JsonMappingException globally so malformed JSON
 * responses use the consistent ProblemDetails format across the API.
 */
@Provider
@ApplicationScoped
public class JacksonJsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {

    @Inject
    CoreRegistryExceptionMapperService coreMapper;

    @Override
    public Response toResponse(JsonMappingException exception) {
        String actualValue = extractInvalidStatusValue(exception);

        if (actualValue != null) {
            return coreMapper.mapException(new InvalidParameterValueException("status", "valid status enum value", actualValue));
        }

        String message = exception.getOriginalMessage();

        if (message != null && !message.contains("io.apicurio")) {
            return coreMapper.mapException(new BadRequestException(message));
        }

        return coreMapper.mapException(
            new BadRequestException("Not able to deserialize data provided.")
        );
    }

    /**
     * If the given exception represents an invalid value assigned to a
     * {@link ContractStatusTransition.Status} enum field, and the offending raw value can be
     * determined, returns that raw value. Returns null otherwise (either the exception is
     * unrelated to that enum, or the raw value could not be determined), in which case the
     * caller falls back to generic message-based handling.
     */
    private String extractInvalidStatusValue(JsonMappingException exception) {
        if (exception instanceof ValueInstantiationException vie) {
            if (vie.getType() != null && ContractStatusTransition.Status.class.equals(vie.getType().getRawClass())) {
                return extractValueFromCause(vie.getCause());
            }
        } else if (exception instanceof InvalidFormatException ife) {
            if (ContractStatusTransition.Status.class.equals(ife.getTargetType()) && ife.getValue() != null) {
                return String.valueOf(ife.getValue());
            }
        }
        return null;
    }

    private String extractValueFromCause(Throwable cause) {
        if (cause instanceof IllegalArgumentException) {
            String message = cause.getMessage();
            if (message != null && !message.isBlank() && !message.contains(" ") && !message.contains(".")) {
                return message;
            }
        }
        return null;
    }
}
