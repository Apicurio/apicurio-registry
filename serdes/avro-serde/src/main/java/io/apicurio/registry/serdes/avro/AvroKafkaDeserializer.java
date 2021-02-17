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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serdes.AbstractKafkaDeserializer;
import io.apicurio.registry.serdes.utils.HeaderUtils;
import io.apicurio.registry.serdes.utils.Utils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Ales Justin
 */
public class AvroKafkaDeserializer<U> extends AbstractKafkaDeserializer<Schema, U, AvroKafkaDeserializer<U>> {
    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private AvroDatumProvider<U> avroDatumProvider;
    private AvroEncoding configEncoding;

    public AvroKafkaDeserializer() {
        this(null);
    }

    public AvroKafkaDeserializer(RegistryClient client) {
        this(client, new DefaultAvroDatumProvider<>());
    }

    public AvroKafkaDeserializer(RegistryClient client, AvroDatumProvider<U> avroDatumProvider) {
        super(client);
        setAvroDatumProvider(avroDatumProvider);
    }

    public AvroKafkaDeserializer<U> setAvroDatumProvider(AvroDatumProvider<U> avroDatumProvider) {
        this.avroDatumProvider = Objects.requireNonNull(avroDatumProvider);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        configEncoding = AvroEncoding.fromConfig(configs);
        // Always add headerUtils, so consumer can read both formats i.e. id stored in header or magic byte
        headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);
        Object adp = configs.get(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM);
        //noinspection rawtypes
        Consumer<AvroDatumProvider> consumer = this::setAvroDatumProvider;
        Utils.instantiate(AvroDatumProvider.class, adp, consumer);
        avroDatumProvider.configure(configs);
    }



//    @Override
//    protected Schema toSchema(InputStream schemaData) {
//        return AvroSchemaUtils.parse(IoUtil.toString(schemaData));
//    }

    /**
     * @see io.apicurio.registry.serdes.SchemaMapper#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.AVRO;
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaMapper#schemaFromData(java.lang.Object)
     */
    @Override
    public Schema schemaFromData(U data) {
        //not needed for deserialization
        return null;
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaMapper#parseSchema(java.io.InputStream)
     */
    @Override
    public Schema parseSchema(InputStream rawSchema) {
        return AvroSchemaUtils.parse(IoUtil.toString(rawSchema));
    }

    @Override
    protected U readData(Schema schema, ByteBuffer buffer, int start, int length) {
        return readData(null, schema, buffer, start, length);
    }

    @Override
    protected U readData(Headers headers, Schema schema, ByteBuffer buffer, int start, int length) {
        AvroEncoding encoding = null;
        if (headers != null && headerUtils != null){
            String encodingHeader = headerUtils.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding == null) {
            // no encoding in header or no headers so use config
            encoding = configEncoding;
        }
        try {
            DatumReader<U> reader = avroDatumProvider.createDatumReader(schema);
            if( encoding == AvroEncoding.JSON) {
                // copy the data into a new byte[]
                byte[] msgData = new byte[length];
                System.arraycopy(buffer.array(), start, msgData, 0, length);
                return reader.read(null, decoderFactory.jsonDecoder(schema, new ByteArrayInputStream(msgData)));
            } else {
                return reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
