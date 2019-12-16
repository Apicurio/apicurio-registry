/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.utils.serde;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.utils.serde.avro.AvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.AvroSchemaUtils;
import io.apicurio.registry.utils.serde.avro.DefaultAvroDatumProvider;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class AvroKafkaDeserializer<U> extends AbstractKafkaDeserializer<Schema, U, AvroKafkaDeserializer<U>> {
    private final DecoderFactory decoderFactory = DecoderFactory.get();
    private AvroDatumProvider<U> avroDatumProvider;

    public AvroKafkaDeserializer() {
        this(null);
    }

    public AvroKafkaDeserializer(RegistryService client) {
        this(client, new DefaultAvroDatumProvider<>());
    }

    public AvroKafkaDeserializer(RegistryService client, AvroDatumProvider<U> avroDatumProvider) {
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

        Object adp = configs.get(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM);
        //noinspection unchecked,rawtypes
        Consumer<AvroDatumProvider> consumer =
            ((Consumer<AvroDatumProvider>) avroDatumProvider -> avroDatumProvider.configure(configs))
                .andThen(this::setAvroDatumProvider);
        instantiate(AvroDatumProvider.class, adp, consumer);
    }

    @Override
    protected Schema toSchema(Response response) {
        return AvroSchemaUtils.parse(response.readEntity(String.class));
    }

    @Override
    protected U readData(Schema schema, ByteBuffer buffer, int start, int length) {
        try {
            DatumReader<U> reader = avroDatumProvider.createDatumReader(schema);
            return reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
