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

package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;

/**
 * @author Ales Justin
 */
public class ReflectAvroDatumProvider<T> implements AvroDatumProvider<T> {

    private Schema readerSchema;

    public ReflectAvroDatumProvider() {
    }

    public ReflectAvroDatumProvider(Class<T> clazz) {
        this.readerSchema = AvroSchemaUtils.getReflectSchema(clazz);
    }

    @Override
    public DatumWriter<T> createDatumWriter(T data, Schema schema) {
        return new ReflectDatumWriter<>(schema);
    }

    @Override
    public DatumReader<T> createDatumReader(Schema schema) {
        if (readerSchema == null) {
            return new ReflectDatumReader<>(schema);
        } else {
            return new ReflectDatumReader<>(schema, readerSchema);
        }
    }

    @Override
    public Schema toSchema(T data) {
        return AvroSchemaUtils.getReflectSchema(data);
    }
}
