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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldturner.medeia.api.StringSchemaSource;
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi;
import com.worldturner.medeia.schema.validation.SchemaValidator;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.utils.IoUtil;
import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.rw.CloudEventAttributesWriter;
import io.cloudevents.rw.CloudEventExtensionsWriter;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventReader;
import io.cloudevents.rw.CloudEventWriter;
import io.cloudevents.rw.CloudEventWriterFactory;

/**
 * @author Fabian Martinez
 */
public class JsonSchemaCloudEventsSerde {

        protected static MedeiaJacksonApi api = new MedeiaJacksonApi();
        protected static ObjectMapper mapper = new ObjectMapper();

        private RegistryRestClient registryClient;
        private DataSchemaCache<SchemaValidator> schemaValidatorCache;

        public JsonSchemaCloudEventsSerde(RegistryRestClient registryClient) {
            this.registryClient = registryClient;
        }

        public <T> T readData(CloudEvent cloudevent, Class<T> clazz) {
            if (cloudevent.getData() == null) {
                return null;
            }

            try {
                DataSchemaEntry<SchemaValidator> dataschema = getSchemaValidatorCache().getSchema(cloudevent);
                SchemaValidator schemaValidator = dataschema.getSchema();


                JsonParser parser = mapper.getFactory().createParser(cloudevent.getData());
                parser = api.decorateJsonParser(schemaValidator, parser);

                return mapper.readValue(parser, clazz);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public <T> ParsedData<T> readParsedData(CloudEvent cloudevent, Class<T> clazz) {
            if (cloudevent.getData() == null) {
                return null;
            }

            try {
                DataSchemaEntry<SchemaValidator> dataschema = getSchemaValidatorCache().getSchema(cloudevent);
                SchemaValidator schemaValidator = dataschema.getSchema();


                JsonParser parser = mapper.getFactory().createParser(cloudevent.getData());
                parser = api.decorateJsonParser(schemaValidator, parser);

                T data = mapper.readValue(parser, clazz);

                return new ParsedData<T>(dataschema.getDataSchema(), data);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public <T> CloudEvent writeData(CloudEvent cloudevent, T data) {
            if (data == null) {
                return cloudevent;
            }

            try {
                DataSchemaEntry<SchemaValidator> dataschema = getSchemaValidatorCache().getSchema(cloudevent);
                SchemaValidator schemaValidator = dataschema.getSchema();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                JsonGenerator generator = mapper.getFactory().createGenerator(baos);
                generator = api.decorateJsonGenerator(schemaValidator, generator);

                mapper.writeValue(generator, data);

                return new CloudEventDelegate(cloudevent, "application/json", baos.toByteArray(), dataschema.getDataSchema());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private synchronized DataSchemaCache<SchemaValidator> getSchemaValidatorCache() {
            if (schemaValidatorCache == null) {
                schemaValidatorCache = new DataSchemaCache<SchemaValidator>(registryClient) {
                    @Override
                    protected SchemaValidator toSchema(InputStream rawSchema) {
                        return api.loadSchema(new StringSchemaSource(IoUtil.toString(rawSchema)));
                    }
                };
            }
            return schemaValidatorCache;
        }

        public static class CloudEventDelegate implements CloudEvent, CloudEventReader {

            private CloudEvent delegate;
            private String datacontenttype;
            private byte[] data;
            private URI dataschema;

            public CloudEventDelegate(CloudEvent delegate, String datacontenttype, byte[] data, String dataschema) {
                this.delegate = delegate;
                this.datacontenttype = datacontenttype;
                this.data = data;
                this.dataschema = URI.create(dataschema);
            }

            @Override
            public SpecVersion getSpecVersion() {
                return delegate.getSpecVersion();
            }

            @Override
            public String getId() {
                return delegate.getId();
            }

            @Override
            public String getType() {
                return delegate.getType();
            }

            @Override
            public URI getSource() {
                return delegate.getSource();
            }

            @Override
            public String getDataContentType() {
                return datacontenttype;
            }

            @Override
            public URI getDataSchema() {
                return dataschema;
            }

            @Override
            public String getSubject() {
                return delegate.getSubject();
            }

            @Override
            public OffsetDateTime getTime() {
                return delegate.getTime();
            }

            @Override
            public Object getAttribute(String attributeName) throws IllegalArgumentException {
                return delegate.getAttribute(attributeName);
            }

            @Override
            public Object getExtension(String extensionName) {
                return delegate.getExtension(extensionName);
            }

            @Override
            public Set<String> getExtensionNames() {
                return delegate.getExtensionNames();
            }

            @Override
            public byte[] getData() {
                return data;
            }

            @Override
            public <T extends CloudEventWriter<V>, V> V read(CloudEventWriterFactory<T, V> writerFactory) throws CloudEventRWException, IllegalStateException {
                CloudEventWriter<V> visitor = writerFactory.create(this.getSpecVersion());
                this.readAttributes(visitor);
                this.readExtensions(visitor);

                if (this.data != null) {
                    return visitor.end(this.data);
                }

                return visitor.end();
            }

            @Override
            public void readAttributes(CloudEventAttributesWriter writer) throws CloudEventRWException {
                writer.withAttribute(
                        "id",
                        this.getId()
                    );
                    writer.withAttribute(
                        "source",
                        this.getSource()
                    );
                    writer.withAttribute(
                        "type",
                        this.getType()
                    );
                    if (this.datacontenttype != null) {
                        writer.withAttribute(
                            "datacontenttype",
                            this.datacontenttype
                        );
                    }
                    if (this.getDataSchema() != null) {
                        writer.withAttribute(
                            "dataschema",
                            this.getDataSchema()
                        );
                    }
                    if (this.getSubject() != null) {
                        writer.withAttribute(
                            "subject",
                            this.getSubject()
                        );
                    }
                    if (this.getTime() != null) {
                        writer.withAttribute(
                            "time",
                            this.getTime()
                        );
                    }
            }

            @Override
            public void readExtensions(CloudEventExtensionsWriter visitor) throws CloudEventRWException {
                // TODO to be improved
                for (String extensionName : this.getExtensionNames()) {
                    Object extension = getExtension(extensionName);
                    if (extension instanceof String) {
                        visitor.withExtension(extensionName, (String) extension);
                    } else if (extension instanceof Number) {
                        visitor.withExtension(extensionName, (Number) extension);
                    } else if (extension instanceof Boolean) {
                        visitor.withExtension(extensionName, (Boolean) extension);
                    } else {
                        // This should never happen because we build that map only through our builders
                        throw new IllegalStateException("Illegal value inside extensions map: " + extension);
                    }
                }
            }

        }

}
