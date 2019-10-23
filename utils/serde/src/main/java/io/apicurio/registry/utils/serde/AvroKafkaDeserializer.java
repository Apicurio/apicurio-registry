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
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class AvroKafkaDeserializer<U> extends AbstractKafkaDeserializer<Schema, U> {
    private final DecoderFactory decoderFactory = DecoderFactory.get();

    public AvroKafkaDeserializer(RegistryService client) {
        super(client);
    }

    private DatumReader getDatumReader(Schema schema) {
        boolean writerSchemaIsPrimitive = AvroSchemaUtils.getPrimitiveSchemas().containsValue(schema);
        // do not use SpecificDatumReader if schema is a primitive
        if (writerSchemaIsPrimitive) {
            return new GenericDatumReader(schema);
        } else {
            return new SpecificDatumReader(schema);
        }
    }

    @Override
    protected Schema toSchema(Response response) {
        return AvroSchemaUtils.parse(response.readEntity(String.class));
    }

    @Override
    protected U readData(Schema schema, ByteBuffer buffer, int start, int length) {
        try {
            DatumReader reader = getDatumReader(schema);
            //noinspection unchecked
            return (U) reader.read(null, decoderFactory.binaryDecoder(buffer.array(), start, length, null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
