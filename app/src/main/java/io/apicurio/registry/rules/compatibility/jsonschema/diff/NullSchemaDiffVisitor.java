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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NullSchemaWrapper;
import org.everit.json.schema.NullSchema;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class NullSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final NullSchema original;

    public NullSchemaDiffVisitor(DiffContext ctx, NullSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitNullSchema(NullSchemaWrapper schema) {
        super.visitNullSchema(schema);
    }
}
