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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Ales Justin
 */
public class AvroKafkaSerializer<U> extends AbstractKafkaSerializer<Schema, U, AvroKafkaSerializer<U>> {
    private final EncoderFactory encoderFactory = EncoderFactory.get();

    public AvroKafkaSerializer() {
    }

    public AvroKafkaSerializer(RegistryService client) {
        super(client);
    }

    public AvroKafkaSerializer(RegistryService client, ArtifactIdStrategy<Schema> artifactIdStrategy, GlobalIdStrategy<Schema> idStrategy) {
        super(client, artifactIdStrategy, idStrategy);
    }

    @Override
    protected Schema toSchema(U data) {
        return AvroSchemaUtils.getSchema(data);
    }

    @Override
    protected ArtifactType artifactType() {
        return ArtifactType.AVRO;
    }

    @Override
    protected void serializeData(Schema schema, U data, OutputStream out) throws IOException {
        BinaryEncoder encoder = encoderFactory.directBinaryEncoder(out, null);
        DatumWriter<Object> writer;
        if (data instanceof SpecificRecord) {
            writer = new SpecificDatumWriter<>(schema);
        } else {
            writer = new GenericDatumWriter<>(schema);
        }
        writer.write(data, encoder);
        encoder.flush();
    }
}
