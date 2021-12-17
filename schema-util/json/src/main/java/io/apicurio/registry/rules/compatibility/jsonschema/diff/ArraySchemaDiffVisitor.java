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
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.Schema;

import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_EXTENDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_BOOLEAN_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_NARROWED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ALL_ITEM_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ALL_ITEM_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ITEM_SCHEMA_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ITEM_SCHEMA_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ITEM_SCHEMAS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_ITEM_SCHEMAS_NARROWED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MAX_ITEMS_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MAX_ITEMS_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MAX_ITEMS_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MAX_ITEMS_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MIN_ITEMS_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MIN_ITEMS_DECREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MIN_ITEMS_INCREASED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_MIN_ITEMS_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_UNIQUE_ITEMS_FALSE_TO_TRUE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_UNIQUE_ITEMS_BOOLEAN_UNCHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ARRAY_TYPE_UNIQUE_ITEMS_TRUE_TO_FALSE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffBooleanTransition;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffInteger;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSchemaOrTrue;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubSchemasAdded;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubSchemasRemoved;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSubschemaAddedRemoved;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.getExceptionally;
import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class ArraySchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private final DiffContext ctx;
    private final ArraySchema original;
    private ArraySchemaWrapper schema;

    public ArraySchemaDiffVisitor(DiffContext ctx, ArraySchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitArraySchema(ArraySchemaWrapper arraySchema) {
        ctx.log("Visiting " + arraySchema + " at " + arraySchema.getWrapped().getLocation());
        this.schema = arraySchema;
        super.visitArraySchema(arraySchema);
    }

    @Override
    public void visitMinItems(Integer minItems) {
        diffInteger(ctx.sub("minItems"), original.getMinItems(), minItems,
            ARRAY_TYPE_MIN_ITEMS_ADDED,
            ARRAY_TYPE_MIN_ITEMS_REMOVED,
            ARRAY_TYPE_MIN_ITEMS_INCREASED,
            ARRAY_TYPE_MIN_ITEMS_DECREASED);
        super.visitMinItems(minItems);
    }

    @Override
    public void visitMaxItems(Integer maxItems) {
        diffInteger(ctx.sub("maxItems"), original.getMaxItems(), maxItems,
            ARRAY_TYPE_MAX_ITEMS_ADDED,
            ARRAY_TYPE_MAX_ITEMS_REMOVED,
            ARRAY_TYPE_MAX_ITEMS_INCREASED,
            ARRAY_TYPE_MAX_ITEMS_DECREASED);
        super.visitMaxItems(maxItems);
    }

    @Override
    public void visitUniqueItems(boolean uniqueItems) {
        diffBooleanTransition(ctx.sub("uniqueItems"), original.needsUniqueItems(), uniqueItems,false,
            ARRAY_TYPE_UNIQUE_ITEMS_FALSE_TO_TRUE,
            ARRAY_TYPE_UNIQUE_ITEMS_TRUE_TO_FALSE,
            ARRAY_TYPE_UNIQUE_ITEMS_BOOLEAN_UNCHANGED);
        super.visitUniqueItems(uniqueItems);
    }

    @Override
    public void visitAllItemSchema(SchemaWrapper allItemSchema) {
        ctx.log("visitAllItemSchema: " + allItemSchema + " orig.: " + original.getAllItemSchema());
        DiffContext subCtx = ctx.sub("allItemSchema");
        if (diffSubschemaAddedRemoved(subCtx, original.getAllItemSchema(), allItemSchema,
            ARRAY_TYPE_ALL_ITEM_SCHEMA_ADDED,
            ARRAY_TYPE_ALL_ITEM_SCHEMA_REMOVED)) {
            allItemSchema.accept(new SchemaDiffVisitor(subCtx, original.getAllItemSchema()));
        }
        super.visitAllItemSchema(allItemSchema);
    }

    @Override
    public void visitAdditionalItems(boolean additionalItems) {
        ctx.log("visitAdditionalItems: " + additionalItems);
        if (diffBooleanTransition(ctx.sub("additionalItems"), original.permitsAdditionalItems(), additionalItems, true,
                ARRAY_TYPE_ADDITIONAL_ITEMS_FALSE_TO_TRUE,
                ARRAY_TYPE_ADDITIONAL_ITEMS_TRUE_TO_FALSE,
                ARRAY_TYPE_ADDITIONAL_ITEMS_BOOLEAN_UNCHANGED)) {

            if (additionalItems) {
                // both original and updated permit additionalItems
                Schema updatedSchemaOfAdditionalItems =
                        schema.getSchemaOfAdditionalItems() == null ? null : schema.getSchemaOfAdditionalItems().getWrapped();
                diffSchemaOrTrue(ctx.sub("schemaOfAdditionalItems"), original.getSchemaOfAdditionalItems(),
                        updatedSchemaOfAdditionalItems, ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_UNCHANGED,
                        ARRAY_TYPE_ADDITIONAL_ITEMS_EXTENDED, ARRAY_TYPE_ADDITIONAL_ITEMS_NARROWED,
                        ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_CHANGED);
            }
        }
        super.visitAdditionalItems(additionalItems);
    }

    @Override
    public void visitItemSchemas(List<SchemaWrapper> itemSchemas) {
        ctx.log("visitItemSchemas: " + itemSchemas);
        int originalSize = Optional.ofNullable(original.getItemSchemas()).map(s -> s.size()).orElse(0);
        int updatedSize = Optional.ofNullable(itemSchemas).map(s -> s.size()).orElse(0);
        int size = Math.min(originalSize, updatedSize);
        for (int i = 0; i < size; ++i) {
            visitItemSchema(i, itemSchemas.get(i));
        }

        if (updatedSize > size) { // adding items
            diffSubSchemasAdded(ctx.sub("addItemSchema"), itemSchemas.subList(size, updatedSize),
                original.permitsAdditionalItems(), wrap(original.getSchemaOfAdditionalItems()),
                schema.permitsAdditionalItems(), ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED,
                ARRAY_TYPE_ITEM_SCHEMAS_NARROWED, ARRAY_TYPE_ITEM_SCHEMAS_CHANGED);
        }
        if (originalSize > size) { // removing items
            diffSubSchemasRemoved(ctx.sub("removeItemSchema"), wrap(original.getItemSchemas().subList(size, originalSize)),
                schema.permitsAdditionalItems(), schema.getSchemaOfAdditionalItems(),
                original.permitsAdditionalItems(), ARRAY_TYPE_ITEM_SCHEMAS_NARROWED,
                ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED, ARRAY_TYPE_ITEM_SCHEMAS_CHANGED);
        }

        super.visitItemSchemas(itemSchemas);
    }

    @Override
    public void visitItemSchema(int index, SchemaWrapper itemSchema) {
        ctx.log("visitItemSchema: " + itemSchema);
        DiffContext subCtx = ctx.sub("items/" + index);
        if (diffSubschemaAddedRemoved(subCtx,
            getExceptionally(subCtx, () -> original.getItemSchemas().get(index)), itemSchema,
            ARRAY_TYPE_ITEM_SCHEMA_ADDED,
            ARRAY_TYPE_ITEM_SCHEMA_REMOVED)) {
            itemSchema.accept(new SchemaDiffVisitor(subCtx, original.getItemSchemas().get(index)));
        }
        super.visitItemSchema(index, itemSchema);
    }

    @Override
    public void visitSchemaOfAdditionalItems(SchemaWrapper schemaOfAdditionalItems) {
        // This is also handled by visitAdditionalItems
        super.visitSchemaOfAdditionalItems(schemaOfAdditionalItems);
    }

    @Override
    public void visitContainedItemSchema(SchemaWrapper containedItemSchema) {
        ctx.log("visitContainedItemSchema: " + containedItemSchema);
        DiffContext subCtx = ctx.sub("containedItemSchema");
        if (diffSubschemaAddedRemoved(subCtx, original.getContainedItemSchema(), containedItemSchema,
            ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_ADDED,
            ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_REMOVED)) {
            containedItemSchema.accept(new SchemaDiffVisitor(subCtx, original.getContainedItemSchema()));
        }
        super.visitContainedItemSchema(containedItemSchema);
    }
}
