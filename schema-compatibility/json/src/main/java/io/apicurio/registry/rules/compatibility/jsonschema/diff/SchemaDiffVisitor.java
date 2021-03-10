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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.StringSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.TrueSchemaWrapper;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.BooleanSchema;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.ConditionalSchema;
import org.everit.json.schema.ConstSchema;
import org.everit.json.schema.EnumSchema;
import org.everit.json.schema.FalseSchema;
import org.everit.json.schema.NotSchema;
import org.everit.json.schema.NullSchema;
import org.everit.json.schema.NumberSchema;
import org.everit.json.schema.ObjectSchema;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.Schema;
import org.everit.json.schema.StringSchema;

import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.SUBSCHEMA_TYPE_CHANGED;
import static java.util.Objects.requireNonNull;
import static org.everit.json.schema.CombinedSchema.ALL_CRITERION;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class SchemaDiffVisitor extends JsonSchemaWrapperVisitor {

    private final DiffContext ctx;
    private final Schema original;

    public SchemaDiffVisitor(DiffContext ctx, Schema original) {
        this.ctx = ctx;
        this.original = getReferencedOrOriginal(original);
    }

    /**
     * In case of e.g. enum of strings (with type property defined as "string"),
     * the schema is not an EnumSchema or a StringSchema, but a CombinedSchema of both.
     * <p>
     * If original is Combined and updated is not, the backwards compatibility is
     * satisfied iff the Combined schema contains a schema that is compatible with updated (and their type matches).
     * <p>
     * This should only work for allOf criterion however.
     */
    private Schema getCompatibleSubschemaOrOriginal(Schema original, SchemaWrapper updated) {
        requireNonNull(original);
        requireNonNull(updated);
        if (original instanceof CombinedSchema) {
            Set<Schema> typeCompatible = ((CombinedSchema) original).getSubschemas().stream()
                .filter(s -> s.getClass().isInstance(updated.getWrapped()))
                .collect(Collectors.toSet());
            if (ALL_CRITERION.equals(((CombinedSchema) original).getCriterion()) && typeCompatible.size() == 1)
                return typeCompatible.stream().findAny().get();
        }
        return original;
    }

    private Schema getReferencedOrOriginal(Schema original) {
        requireNonNull(original);
        if /* while */ (original instanceof ReferenceSchema) {
            original = ((ReferenceSchema) original).getReferredSchema();
        }
        return original;
    }

    @Override
    public void visitSchema(SchemaWrapper schema) {
        super.visitSchema(schema);
    }

    @Override
    public void visitStringSchema(StringSchemaWrapper stringSchema) {
        Schema subschema = getCompatibleSubschemaOrOriginal(original, stringSchema); // In case of enum

        if (subschema instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(subschema instanceof StringSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, subschema, stringSchema);
            return;
        }
        // ctx is assumed to already contain the path
        stringSchema.accept(new StringSchemaDiffVisitor(ctx, (StringSchema) subschema));
    }

    @Override
    public void visitArraySchema(ArraySchemaWrapper arraySchema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

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
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(original instanceof ObjectSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new ObjectSchemaDiffVisitor(ctx, (ObjectSchema) original));
    }

    @Override
    public void visitBooleanSchema(BooleanSchemaWrapper schema) {
        Schema subschema = getCompatibleSubschemaOrOriginal(original, schema); // In case of enum

        if (subschema instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(subschema instanceof BooleanSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, subschema, schema);
            return;
        }
        schema.accept(new BooleanSchemaDiffVisitor(ctx, (BooleanSchema) subschema));
    }

    @Override
    public void visitConstSchema(ConstSchemaWrapper schema) {
        Schema orig = original;

        if (orig instanceof FalseSchema)
            return; // FalseSchema matches nothing

        // Const and single-enum equivalency
        if (orig instanceof EnumSchema) {
            Set<Object> possibleValues = ((EnumSchema) orig).getPossibleValues();
            if (possibleValues.size() == 1) {
                orig = ConstSchema.builder()
                    .permittedValue(possibleValues.stream().findAny().get())
                    .build();
            }
        }

        if (!(orig instanceof ConstSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, orig, schema);
            return;
        }
        schema.accept(new ConstSchemaDiffVisitor(ctx, (ConstSchema) orig));
    }

    @Override
    public void visitEnumSchema(EnumSchemaWrapper schema) {
        Schema orig = original;

        if (orig instanceof FalseSchema)
            return; // FalseSchema matches nothing

        // Const and single-enum equivalency
        if (orig instanceof ConstSchema) {
            Object permittedValue = ((ConstSchema) orig).getPermittedValue();
            orig = EnumSchema.builder()
                .possibleValue(permittedValue)
                .build();
        }

        if (!(orig instanceof EnumSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, orig, schema);
            return;
        }
        schema.accept(new EnumSchemaDiffVisitor(ctx, (EnumSchema) orig));
    }

    @Override
    public void visitNullSchema(NullSchemaWrapper schema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(original instanceof NullSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new NullSchemaDiffVisitor(ctx, (NullSchema) original));
    }

    @Override
    public void visitCombinedSchema(CombinedSchemaWrapper schema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(original instanceof CombinedSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new CombinedSchemaDiffVisitor(ctx, (CombinedSchema) original));
    }

    @Override
    public void visitConditionalSchema(ConditionalSchemaWrapper schema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(original instanceof ConditionalSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new ConditionalSchemaDiffVisitor(ctx, (ConditionalSchema) original));
    }

    @Override
    public void visitNotSchema(NotSchemaWrapper schema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(original instanceof NotSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, original, schema);
            return;
        }
        schema.accept(new NotSchemaDiffVisitor(ctx, (NotSchema) original));
    }

    @Override
    public void visitNumberSchema(NumberSchemaWrapper schema) {
        Schema subschema = getCompatibleSubschemaOrOriginal(original, schema); // In case of enum

        if (subschema instanceof FalseSchema)
            return; // FalseSchema matches nothing

        if (!(subschema instanceof NumberSchema)) {
            ctx.addDifference(SUBSCHEMA_TYPE_CHANGED, subschema, schema);
            return;
        }
        schema.accept(new NumberSchemaDiffVisitor(ctx, (NumberSchema) subschema));
    }

    @Override
    public void visitReferenceSchema(ReferenceSchemaWrapper schema) {
        if (original instanceof FalseSchema)
            return; // FalseSchema matches nothing

        // Original does not have to be reference schema, it could have been replaced by ref,
        // which may be ok
        schema.accept(new ReferenceSchemaDiffVisitor(ctx, original));
    }
}
