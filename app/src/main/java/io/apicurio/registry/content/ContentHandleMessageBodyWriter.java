package io.apicurio.registry.content;

import io.apicurio.registry.utils.IoUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;


@Provider
@Produces(MediaType.WILDCARD)
public class ContentHandleMessageBodyWriter implements MessageBodyWriter<ContentHandle> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return ContentHandle.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(
        ContentHandle content,
        Class<?> type,
        Type genericType,
        Annotation[] annotations,
        MediaType mediaType,
        MultivaluedMap<String, Object> httpHeaders,
        OutputStream entityStream
    ) throws IOException, WebApplicationException {
        try (InputStream stream = content.stream()) {
            IoUtil.copy(stream, entityStream);
        }
    }
}