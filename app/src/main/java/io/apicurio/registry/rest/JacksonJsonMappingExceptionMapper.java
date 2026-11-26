package io.apicurio.registry.rest;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.apicurio.registry.rest.v3.beans.ContractStatusTransition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Intentionally handles JsonMappingException globally so malformed JSON is converted once. The
 * resulting error is delegated to {@link RegistryExceptionMapper} so each API surface preserves
 * its response contract.
 */
@Provider
@ApplicationScoped
public class JacksonJsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {

    @Inject
    RegistryExceptionMapper registryExceptionMapper;

    @Override
    public Response toResponse(JsonMappingException exception) {
        String actualValue = extractInvalidStatusValue(exception);

        if (actualValue != null) {
            return map(new InvalidParameterValueException("status", "valid status enum value", actualValue));
        }

        String message = exception.getOriginalMessage();

        if (message != null && !message.contains("io.apicurio")) {
            return map(new BadRequestException(message));
        }

        return map(
            new BadRequestException("Not able to deserialize data provided.")
        );
    }

    /**
     * This mapper is selected by exception type, so delegate explicitly to the registry mapper to
     * preserve API-surface-specific error responses.
     */
    private Response map(Throwable t) {
        return registryExceptionMapper.toResponse(t);
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
