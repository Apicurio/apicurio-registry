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

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Special implementation of the ParsedSchema interface that delegates to a supplier function to delay the loading of the parsed schema value.
 * This is useful to avoid unnecessarily parsing the schema in the case the serializers don't require it.
 *
 * @author Fabian Martinez
 */
public class LazyLoadedParsedSchema<T> implements ParsedSchema<T> {

    private Optional<ParsedSchema<T>> value;

    private Supplier<Optional<ParsedSchema<T>>> schemaLoader;

    public LazyLoadedParsedSchema(Supplier<Optional<ParsedSchema<T>>> schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    /**
     * @see io.apicurio.registry.serde.ParsedSchema#getParsedSchema()
     */
    @Override
    public T getParsedSchema() {
        if (value == null) {
            value = schemaLoader.get();
        }
        return value.map(ParsedSchema<T>::getParsedSchema).orElse(null);
    }

    /**
     * @see io.apicurio.registry.serde.ParsedSchema#getRawSchema()
     */
    @Override
    public byte[] getRawSchema() {
        if (value == null) {
            value = schemaLoader.get();
        }
        return value.map(ParsedSchema<T>::getRawSchema).orElse(null);
    }

}
