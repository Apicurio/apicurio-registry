/*
 * Copyright 2019 Red Hat
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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ArraySchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.BooleanSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.CombinedSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConditionalSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConstSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EmptySchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EnumSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.FalseSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NotSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NullSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NumberSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ObjectSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ReferenceSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.TrueSchemaWrapper;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.BooleanSchema;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.ConditionalSchema;
import org.everit.json.schema.ConstSchema;
import org.everit.json.schema.EnumSchema;
import org.everit.json.schema.NotSchema;
import org.everit.json.schema.NullSchema;
import org.everit.json.schema.NumberSchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.StringSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.SUBSCHEMA_TYPE_CHANGED;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class SchemaDiffVisitor extends JsonSchemaWrapperVisitor {

    private final DiffContext ctx;
    private final Schema original;

    public SchemaDiffVisitor(DiffContext ctx, Schema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitStringSchema(StringSchemaWrapper stringSchema) {
        if (!(original instanceof StringSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, stringSchema);
            return;
        }
        // ctx is assumed to already contain the path
        stringSchema.accept(new StringSchemaDiffVisitor(ctx, (StringSchema) original));
    }

    @Override
    public void visitArraySchema(ArraySchemaWrapper arraySchema) {
        if (!(original instanceof ArraySchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, arraySchema);
            return;
        }
        arraySchema.accept(new ArraySchemaDiffVisitor(ctx, (ArraySchema) original));
    }

    @Override
    public void visitEmptySchema(EmptySchemaWrapper schema) {
        schema.accept(new PrimitiveSchemaDiffVisitor(ctx, original));
    }

    @Override
    public void visitTrueSchema(TrueSchemaWrapper schema) {
        schema.accept(new PrimitiveSchemaDiffVisitor(ctx, original));
    }

    @Override
    public void visitFalseSchema(FalseSchemaWrapper schema) {
        schema.accept(new PrimitiveSchemaDiffVisitor(ctx, original));
    }

    @Override
    public void visitObjectSchema(ObjectSchemaWrapper schema) {
        if (!(original instanceof ObjectSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new ObjectSchemaDiffVisitor(ctx, (ObjectSchema) original));
    }

    @Override
    public void visitBooleanSchema(BooleanSchemaWrapper schema) {
        if (!(original instanceof BooleanSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new BooleanSchemaDiffVisitor(ctx, (BooleanSchema) original));
    }

    @Override
    public void visitConstSchema(ConstSchemaWrapper schema) {
        if (!(original instanceof ConstSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new ConstSchemaDiffVisitor(ctx, (ConstSchema) original));
    }

    @Override
    public void visitEnumSchema(EnumSchemaWrapper schema) {
        if (!(original instanceof EnumSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new EnumSchemaDiffVisitor(ctx, (EnumSchema) original));
    }

    @Override
    public void visitNullSchema(NullSchemaWrapper schema) {
        if (!(original instanceof NullSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new NullSchemaDiffVisitor(ctx, (NullSchema) original));
    }

    @Override
    public void visitCombinedSchema(CombinedSchemaWrapper schema) {
        if (!(original instanceof CombinedSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new CombinedSchemaDiffVisitor(ctx, (CombinedSchema) original));
    }

    @Override
    public void visitConditionalSchema(ConditionalSchemaWrapper schema) {
        if (!(original instanceof ConditionalSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new ConditionalSchemaDiffVisitor(ctx, (ConditionalSchema) original));
    }

    @Override
    public void visitNotSchema(NotSchemaWrapper schema) {
        if (!(original instanceof NotSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new NotSchemaDiffVisitor(ctx, (NotSchema) original));
    }

    @Override
    public void visitNumberSchema(NumberSchemaWrapper schema) {
        if (!(original instanceof NumberSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new NumberSchemaDiffVisitor(ctx, (NumberSchema) original));
    }

    @Override
    public void visitReferenceSchema(ReferenceSchemaWrapper schema) {
        // Original does not have to be reference schema, it could have been replaced by ref,
        // which may be ok
        schema.accept(new ReferenceSchemaDiffVisitor(ctx, original));
    }
}
