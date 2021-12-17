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
import org.everit.json.schema.ArraySchema;

import java.util.List;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@ToString
public class ArraySchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ArraySchema wrapped;

    public ArraySchemaWrapper(ArraySchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public Integer getMinItems() {
        return wrapped.getMinItems();
    }

    public Integer getMaxItems() {
        return wrapped.getMaxItems();
    }

    public boolean needsUniqueItems() {
        return wrapped.needsUniqueItems();
    }

    public SchemaWrapper getAllItemSchema() {
        return wrap(wrapped.getAllItemSchema());
    }

    public boolean permitsAdditionalItems() {
        return wrapped.permitsAdditionalItems();
    }

    public List<SchemaWrapper> getItemSchemas() {
        return wrap(wrapped.getItemSchemas());
    }

    public SchemaWrapper getSchemaOfAdditionalItems() {
        return wrap(wrapped.getSchemaOfAdditionalItems());
    }

    public SchemaWrapper getContainedItemSchema() {
        return wrap(wrapped.getContainedItemSchema());
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitArraySchema(this);
    }
}
