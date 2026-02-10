package io.apicurio.registry.ccompat.rest.v8;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * JAX-RS ReaderInterceptor that handles the X-Confluent-Accept-Unknown-Properties header.
 *
 * When this header is set to "true", unknown JSON properties in request bodies are ignored
 * during deserialization (forward compatibility feature introduced in Confluent Platform v8).
 *
 * This interceptor checks if the property was set by {@link AcceptUnknownPropertiesFilter}
 * and if the request path starts with /apis/ccompat/v8/, it deserializes the body using
 * a custom ObjectMapper that ignores unknown properties.
 */
@Provider
@Priority(Priorities.ENTITY_CODER)
public class AcceptUnknownPropertiesReaderInterceptor implements ReaderInterceptor {

    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        // Check if this is a v8 API request with the accept-unknown-properties header
        Object acceptUnknown = context.getProperty(AcceptUnknownPropertiesFilter.CONTEXT_PROPERTY);

        if (Boolean.TRUE.equals(acceptUnknown)) {
            // Get the target type
            Type genericType = context.getGenericType();
            Class<?> type = context.getType();

            // Read the input stream
            InputStream inputStream = context.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            byte[] bodyBytes = buffer.toByteArray();

            // If body is empty, proceed with normal processing
            if (bodyBytes.length == 0) {
                context.setInputStream(new ByteArrayInputStream(bodyBytes));
                return context.proceed();
            }

            // Try to deserialize with lenient ObjectMapper
            try {
                return LENIENT_MAPPER.readValue(bodyBytes, LENIENT_MAPPER.constructType(genericType));
            } catch (Exception e) {
                // If lenient parsing fails, restore stream and let default processing handle it
                context.setInputStream(new ByteArrayInputStream(bodyBytes));
                return context.proceed();
            }
        }

        // Default behavior - proceed with normal deserialization
        return context.proceed();
    }
}
