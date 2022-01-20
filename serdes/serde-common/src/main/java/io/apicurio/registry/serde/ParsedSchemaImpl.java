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

package io.apicurio.registry.serde;

/**
 * This class is deprecated and eventually will be replaced by {@link io.apicurio.registry.resolver.ParsedSchemaImpl}
 * @author Fabian Martinez
 */
@Deprecated
public class ParsedSchemaImpl<T> implements ParsedSchema<T> {

    private T parsedSchema;
    private byte[] rawSchema;

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
}
