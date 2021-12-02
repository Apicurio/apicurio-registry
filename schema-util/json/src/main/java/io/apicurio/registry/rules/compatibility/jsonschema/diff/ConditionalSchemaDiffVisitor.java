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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConditionalSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.ConditionalSchema;
import org.everit.json.schema.Schema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BOTH;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_NONE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BOTH;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_NONE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_IF_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BOTH;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_NONE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.compareSchema;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class ConditionalSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final ConditionalSchema original;

    public ConditionalSchemaDiffVisitor(DiffContext ctx, ConditionalSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitConditionalSchema(ConditionalSchemaWrapper schema) {
        super.visitConditionalSchema(schema);
    }

    @Override
    public void visitIfSchema(SchemaWrapper ifSchema) {
        Schema o = original.getIfSchema().orElse(null);
        compareSchema(ctx.sub("if"), o, ifSchema.getWrapped(),
            CONDITIONAL_TYPE_IF_SCHEMA_ADDED,
            CONDITIONAL_TYPE_IF_SCHEMA_REMOVED,
            CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BOTH,
            CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
            CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
            CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_NONE);
        super.visitIfSchema(ifSchema);
    }

    @Override
    public void visitThenSchema(SchemaWrapper thenSchema) {
        Schema o = original.getThenSchema().orElse(null);
        compareSchema(ctx.sub("then"), o, thenSchema.getWrapped(),
            CONDITIONAL_TYPE_THEN_SCHEMA_ADDED,
            CONDITIONAL_TYPE_THEN_SCHEMA_REMOVED,
            CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BOTH,
            CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
            CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
            CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_NONE);
        super.visitThenSchema(thenSchema);
    }

    @Override
    public void visitElseSchema(SchemaWrapper elseSchema) {
        Schema o = original.getElseSchema().orElse(null);
        compareSchema(ctx.sub("else"), o, elseSchema.getWrapped(),
            CONDITIONAL_TYPE_ELSE_SCHEMA_ADDED,
            CONDITIONAL_TYPE_ELSE_SCHEMA_REMOVED,
            CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BOTH,
            CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
            CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
            CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_NONE);
        super.visitElseSchema(elseSchema);
    }
}
