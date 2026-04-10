/*
 * Copyright 2026 Red Hat
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

package io.apicurio.schema.validation.avro;

import io.apicurio.registry.resolver.data.Record;
import org.apache.avro.generic.GenericRecord;

public class AvroRecord implements Record<GenericRecord> {

    private final GenericRecord payload;
    private final AvroMetadata metadata;

    public AvroRecord(GenericRecord payload, AvroMetadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    @Override
    public AvroMetadata metadata() {
        return this.metadata;
    }

    @Override
    public GenericRecord payload() {
        return this.payload;
    }

}
