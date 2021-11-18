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

package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.NumberSchema;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ToString
public class NumberSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final NumberSchema wrapped;

    public NumberSchemaWrapper(NumberSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitNumberSchema(this);
    }

    public boolean isExclusiveMinimum() {
        return wrapped.isExclusiveMinimum();
    }

    public Number getMinimum() {
        return wrapped.getMinimum();
    }

    public Number getExclusiveMinimumLimit() {
        return wrapped.getExclusiveMinimumLimit();
    }

    public boolean isExclusiveMaximum() {
        return wrapped.isExclusiveMaximum();
    }

    public Number getMaximum() {
        return wrapped.getMaximum();
    }

    public Number getExclusiveMaximumLimit() {
        return wrapped.getExclusiveMaximumLimit();
    }

    public Number getMultipleOf() {
        return wrapped.getMultipleOf();
    }

    public boolean requiresInteger() {
        return wrapped.requiresInteger();
    }
}
