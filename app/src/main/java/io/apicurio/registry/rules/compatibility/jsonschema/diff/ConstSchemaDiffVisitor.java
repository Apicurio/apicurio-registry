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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConstSchemaWrapper;
import org.everit.json.schema.ConstSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONST_TYPE_VALUE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffObject;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class ConstSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final ConstSchema original;

    public ConstSchemaDiffVisitor(DiffContext ctx, ConstSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitConstSchema(ConstSchemaWrapper schema) {
        super.visitConstSchema(schema);
    }

    @Override
    public void visitConstValue(Object value) {
        diffObject(ctx.sub("const"), original.getPermittedValue(), value,
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            CONST_TYPE_VALUE_CHANGED);
        super.visitConstValue(value);
    }
}
