/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.utils.cloudevents;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.utils.IoUtil;
import io.cloudevents.core.message.MessageReader;
import io.cloudevents.http.restful.ws.impl.RestfulWSMessageFactory;
import io.cloudevents.http.restful.ws.impl.RestfulWSMessageWriter;

/**
 * @author Fabian Martinez
 */
@Provider
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.WILDCARD)
public class CloudEventsWsProvider implements MessageBodyReader<CloudEvent<?>>, MessageBodyWriter<CloudEvent<?>> {

    JsonSchemaCloudEventsSerde serde = new JsonSchemaCloudEventsSerde(RegistryRestClientFactory.create("http://localhost:8080/api"));


    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return CloudEvent.class.isAssignableFrom(type);
    }

    @Override
    public CloudEvent<?> readFrom(Class<CloudEvent<?>> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        MessageReader reader = RestfulWSMessageFactory.create(mediaType, httpHeaders, IoUtil.toBytes(entityStream));
        io.cloudevents.CloudEvent cloudevent = reader.toEvent();

        ParameterizedType pt = (ParameterizedType) genericType;
        Class<?> typeClass = (Class<?>) pt.getActualTypeArguments()[0];

        ParsedData<?> outputParsedData = serde.readParsedData(cloudevent, typeClass);

        return new CloudEventImpl(cloudevent, outputParsedData.getDataschema(), outputParsedData.getData());
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return CloudEvent.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(CloudEvent<?> event, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {

        CloudEventAdapter adapter = new CloudEventAdapter(event);

        io.cloudevents.CloudEvent outputevent = serde.writeData(adapter, event.data());

        new RestfulWSMessageWriter(httpHeaders, entityStream).writeBinary(outputevent);

    }

}