/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.serdes.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.AbstractKafkaSerializer;
import io.apicurio.registry.serdes.SchemaResolver;
import io.apicurio.registry.serdes.SerdeConfigKeys;
import io.apicurio.registry.serdes.strategy.ArtifactIdStrategy;
import io.apicurio.registry.serdes.utils.HeaderUtils;
import io.apicurio.registry.serdes.utils.Utils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Ales Justin
 */
public class AvroKafkaSerializer<U> extends AbstractKafkaSerializer<Schema, U, AvroKafkaSerializer<U>> {
    private final EncoderFactory encoderFactory = EncoderFactory.get();
    private AvroDatumProvider<U> avroDatumProvider = new DefaultAvroDatumProvider<>();
    private AvroEncoding encoding;

    public AvroKafkaSerializer() {
        super();
    }

    public AvroKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AvroKafkaSerializer(RegistryClient client, ArtifactIdStrategy<Schema> artifactIdStrategy, SchemaResolver<Schema, U> schemaResolver) {
        super(client, artifactIdStrategy, schemaResolver);
    }

    public AvroKafkaSerializer(
        RegistryClient client,
        ArtifactIdStrategy<Schema> artifactIdStrategy,
        SchemaResolver<Schema, U> schemaResolver,
        AvroDatumProvider<U> avroDatumProvider
    ) {
        super(client, artifactIdStrategy, schemaResolver);
        setAvroDatumProvider(avroDatumProvider);
    }

    public AvroKafkaSerializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        encoding = AvroEncoding.fromConfig(configs);
        if (Utils.isTrue(configs.get(SerdeConfigKeys.USE_HEADERS))) {
            headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);
        }
        Object adp = configs.get(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM);
        //noinspection rawtypes
        Consumer<AvroDatumProvider> consumer = this::setAvroDatumProvider;
        Utils.instantiate(AvroDatumProvider.class, adp, consumer);
        avroDatumProvider.configure(configs);
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaMapper#parseSchema(java.io.InputStream)
     */
    @Override
    public Schema parseSchema(InputStream rawSchema) {
        return AvroSchemaUtils.parse(IoUtil.toString(rawSchema));
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaMapper#schemaFromData(java.lang.Object)
     */
    @Override
    public Schema schemaFromData(U data) {
        return avroDatumProvider.toSchema(data);
    }

    @Override
    public ArtifactType artifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    protected void serializeData(Schema schema, U data, OutputStream out) throws IOException {
        Encoder encoder = createEncoder(schema, out);

        // I guess this can happen if generics are lost with reflection ...
        if (data instanceof NonRecordContainer) {
            //noinspection unchecked
            data = (U) NonRecordContainer.class.cast(data).getValue();
        }

        DatumWriter<U> writer = avroDatumProvider.createDatumWriter(data, schema);
        writer.write(data, encoder);
        encoder.flush();
    }

    @Override
    protected void serializeData(Headers headers, Schema schema, U data, ByteArrayOutputStream out) throws IOException {
        if (headerUtils != null) {
            headerUtils.addEncodingHeader(headers, encoding.name());
        }
        serializeData(schema, data, out);
    }

    private Encoder createEncoder(Schema schema, OutputStream os) throws IOException {
        if(encoding == AvroEncoding.JSON) {
            return encoderFactory.jsonEncoder(schema, os);
        } else {
            return encoderFactory.directBinaryEncoder(os, null);
        }
    }
}
