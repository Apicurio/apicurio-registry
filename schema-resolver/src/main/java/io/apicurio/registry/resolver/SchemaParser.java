/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Fabian Martinez
 */
public interface SchemaParser<S, U> {

    public ArtifactType artifactType();

    public S parseSchema(byte[] rawSchema);

    /**
     * This method is useful in serdes such as AVRO, where the schema can be extracted from the data of the kafka record.
     * The result of this method is passed to the SchemaResolver, which then can use this schema to resolve the exact
     * artifact version in Apicurio Registry or to create the artifact if configured to do so.
     *
     * @param data
     * @return the ParsedSchema, containing both the raw schema (bytes) and the parsed schema. Can be null.
     */
    public ParsedSchema<S> getSchemaFromData(Record<U> data);

    default boolean supportsExtractSchemaFromData() {
        return true;
    }
}
