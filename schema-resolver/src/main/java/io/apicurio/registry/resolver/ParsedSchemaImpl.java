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

import java.util.List;

/**
 * @author Fabian Martinez
 */
public class ParsedSchemaImpl<T> implements ParsedSchema<T> {

    private T parsedSchema;
    private byte[] rawSchema;
    private List<ParsedSchema<T>> schemaReferences;
    private String referenceName;

    public ParsedSchemaImpl() {
        //empty
    }

    /**
     * @return the parsedSchema
     */
    @Override
    public T getParsedSchema() {
        return parsedSchema;
    }

    /**
     * @param parsedSchema the parsedSchema to set
     */
    public ParsedSchemaImpl<T> setParsedSchema(T parsedSchema) {
        this.parsedSchema = parsedSchema;
        return this;
    }

    /**
     * @return the rawSchema
     */
    @Override
    public byte[] getRawSchema() {
        return rawSchema;
    }

    /**
     * @param rawSchema the rawSchema to set
     */
    public ParsedSchemaImpl<T> setRawSchema(byte[] rawSchema) {
        this.rawSchema = rawSchema;
        return this;
    }

    /**
     * @return schema references
     */
    public List<ParsedSchema<T>> getSchemaReferences() {
        return schemaReferences;
    }

    /**
     * @param schemaReferences to set
     */
    public ParsedSchemaImpl<T> setSchemaReferences(List<ParsedSchema<T>> schemaReferences) {
        this.schemaReferences = schemaReferences;
        return this;
    }

    @Override
    public boolean hasReferences() {
        return this.schemaReferences != null && !this.schemaReferences.isEmpty();
    }

    @Override
    public String referenceName() {
        return referenceName;
    }

    @Override
    public ParsedSchemaImpl<T> setReferenceName(String referenceName) {
        this.referenceName = referenceName;
        return this;
    }
}
