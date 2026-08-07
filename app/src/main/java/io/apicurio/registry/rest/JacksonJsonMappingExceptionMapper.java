package io.apicurio.registry.rest;

import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.apicurio.registry.rest.v3.beans.ContractStatusTransition;
import io.apicurio.registry.services.http.CoreRegistryExceptionMapperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;

@Provider
@ApplicationScoped
public class JacksonJsonMappingExceptionMapper implements ExceptionMapper<ValueInstantiationException> {

    @Inject
    CoreRegistryExceptionMapperService coreMapper;

    @Context
    Providers providers;

    @Override
    public Response toResponse(ValueInstantiationException exception) {
        if (exception.getType() != null && ContractStatusTransition.Status.class.equals(exception.getType().getRawClass())) {
            String actualValue = "unknown";
            if (exception.getCause() instanceof IllegalArgumentException
                    && exception.getCause().getMessage() != null) {
                actualValue = exception.getCause().getMessage();
            }
            return coreMapper.mapException(new InvalidParameterValueException("status", "valid status enum value", actualValue));
        }

        // Delegate to the framework's default mapper for JsonProcessingException if available
        if (providers != null) {
            ExceptionMapper<com.fasterxml.jackson.core.JsonProcessingException> mapper =
                providers.getExceptionMapper(com.fasterxml.jackson.core.JsonProcessingException.class);
            if (mapper != null && mapper != (Object) this) {
                return mapper.toResponse(exception);
            }
        }

        return Response.status(400).entity("Not able to deserialize data provided.").build();
    }
}
