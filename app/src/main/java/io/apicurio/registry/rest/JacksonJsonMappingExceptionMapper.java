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
        String actualValue = null;
        boolean isStatusEnum = false;

        if (exception instanceof ValueInstantiationException) {
            ValueInstantiationException vie = (ValueInstantiationException) exception;
            if (vie.getType() != null && ContractStatusTransition.Status.class.equals(vie.getType().getRawClass())) {
                isStatusEnum = true;
                if (vie.getCause() instanceof IllegalArgumentException) {
                    String message = vie.getCause().getMessage();
                    if (message != null && !message.isBlank()
                            && !message.contains(" ")
                            && !message.contains(".")) {
                        actualValue = message;
                    }
                }
            }
        } else if (exception instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) exception;
            if (ContractStatusTransition.Status.class.equals(ife.getTargetType())) {
                isStatusEnum = true;
                if (ife.getValue() != null) {
                    actualValue = String.valueOf(ife.getValue());
                }
            }
        }

        if (isStatusEnum && actualValue != null) {
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
}
