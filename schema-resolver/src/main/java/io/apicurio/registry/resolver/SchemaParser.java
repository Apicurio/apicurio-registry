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

import java.util.Map;

/**
 * @author Fabian Martinez
 */
public interface SchemaParser<S, U> {

    public String artifactType();

    public S parseSchema(byte[] rawSchema, Map<String, ParsedSchema<S>> resolvedReferences);

    /**
     * In some artifact types, such as AVRO, it is possible to extract the schema from the java object.
     * But this can be easily extended to other formats by using a custom {@link Record} implementation that adds additional fields
     * that allows to build a {@link ParsedSchema}
     *
     * @param data
     * @return the ParsedSchema, containing both the raw schema (bytes) and the parsed schema. Can be null.
     */
    public ParsedSchema<S> getSchemaFromData(Record<U> data);

    /**
     * Flag that indicates if {@link SchemaParser#getSchemaFromData(Record)} is implemented or not.
     */
    default boolean supportsExtractSchemaFromData() {
        return true;
    }
}
