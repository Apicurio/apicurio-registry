package io.apicurio.registry.json.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.ArraySchemaWrapper;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.ArraySchema;
import org.everit.json.schema.Schema;

import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

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
        DiffUtil.diffInteger(ctx.sub("minItems"), original.getMinItems(), minItems, DiffType.ARRAY_TYPE_MIN_ITEMS_ADDED,
                DiffType.ARRAY_TYPE_MIN_ITEMS_REMOVED, DiffType.ARRAY_TYPE_MIN_ITEMS_INCREASED, DiffType.ARRAY_TYPE_MIN_ITEMS_DECREASED);
        super.visitMinItems(minItems);
    }

    @Override
    public void visitMaxItems(Integer maxItems) {
        DiffUtil.diffInteger(ctx.sub("maxItems"), original.getMaxItems(), maxItems, DiffType.ARRAY_TYPE_MAX_ITEMS_ADDED,
                DiffType.ARRAY_TYPE_MAX_ITEMS_REMOVED, DiffType.ARRAY_TYPE_MAX_ITEMS_INCREASED, DiffType.ARRAY_TYPE_MAX_ITEMS_DECREASED);
        super.visitMaxItems(maxItems);
    }

    @Override
    public void visitUniqueItems(boolean uniqueItems) {
        DiffUtil.diffBooleanTransition(ctx.sub("uniqueItems"), original.needsUniqueItems(), uniqueItems, false,
                DiffType.ARRAY_TYPE_UNIQUE_ITEMS_FALSE_TO_TRUE, DiffType.ARRAY_TYPE_UNIQUE_ITEMS_TRUE_TO_FALSE,
                DiffType.ARRAY_TYPE_UNIQUE_ITEMS_BOOLEAN_UNCHANGED);
        super.visitUniqueItems(uniqueItems);
    }

    @Override
    public void visitAllItemSchema(SchemaWrapper allItemSchema) {
        ctx.log("visitAllItemSchema: " + allItemSchema + " orig.: " + original.getAllItemSchema());
        DiffContext subCtx = ctx.sub("allItemSchema");
        if (DiffUtil.diffSubschemaAddedRemoved(subCtx, original.getAllItemSchema(), allItemSchema,
                DiffType.ARRAY_TYPE_ALL_ITEM_SCHEMA_ADDED, DiffType.ARRAY_TYPE_ALL_ITEM_SCHEMA_REMOVED)) {
            allItemSchema.accept(new SchemaDiffVisitor(subCtx, original.getAllItemSchema()));
        }
        super.visitAllItemSchema(allItemSchema);
    }

    @Override
    public void visitAdditionalItems(boolean additionalItems) {
        ctx.log("visitAdditionalItems: " + additionalItems);
        if (DiffUtil.diffBooleanTransition(ctx.sub("additionalItems"), original.permitsAdditionalItems(),
                additionalItems, true, DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_FALSE_TO_TRUE,
                DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_TRUE_TO_FALSE, DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_BOOLEAN_UNCHANGED)) {

            if (additionalItems) {
                // both original and updated permit additionalItems
                Schema updatedSchemaOfAdditionalItems = schema.getSchemaOfAdditionalItems() == null ? null
                    : schema.getSchemaOfAdditionalItems().getWrapped();
                DiffUtil.diffSchemaOrTrue(ctx.sub("schemaOfAdditionalItems"), original.getSchemaOfAdditionalItems(),
                        updatedSchemaOfAdditionalItems, DiffType.ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_UNCHANGED,
                        DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_EXTENDED, DiffType.ARRAY_TYPE_ADDITIONAL_ITEMS_NARROWED,
                        DiffType.ARRAY_TYPE_SCHEMA_OF_ADDITIONAL_ITEMS_CHANGED);
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
            DiffUtil.diffSubSchemasAdded(ctx.sub("addItemSchema"), itemSchemas.subList(size, updatedSize),
                    original.permitsAdditionalItems(), wrap(original.getSchemaOfAdditionalItems()),
                    schema.permitsAdditionalItems(), DiffType.ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED,
                    DiffType.ARRAY_TYPE_ITEM_SCHEMAS_NARROWED, DiffType.ARRAY_TYPE_ITEM_SCHEMAS_CHANGED);
        }
        if (originalSize > size) { // removing items
            DiffUtil.diffSubSchemasRemoved(ctx.sub("removeItemSchema"),
                    wrap(original.getItemSchemas().subList(size, originalSize)),
                    schema.permitsAdditionalItems(), schema.getSchemaOfAdditionalItems(),
                    original.permitsAdditionalItems(), DiffType.ARRAY_TYPE_ITEM_SCHEMAS_NARROWED,
                    DiffType.ARRAY_TYPE_ITEM_SCHEMAS_NARROWED_COMPATIBLE_WITH_ADDITIONAL_PROPERTIES,
                    DiffType.ARRAY_TYPE_ITEM_SCHEMAS_EXTENDED, DiffType.ARRAY_TYPE_ITEM_SCHEMAS_CHANGED);
        }

        super.visitItemSchemas(itemSchemas);
    }

    @Override
    public void visitItemSchema(int index, SchemaWrapper itemSchema) {
        ctx.log("visitItemSchema: " + itemSchema);
        DiffContext subCtx = ctx.sub("items/" + index);
        if (DiffUtil.diffSubschemaAddedRemoved(subCtx,
                DiffUtil.getExceptionally(subCtx, () -> original.getItemSchemas().get(index)), itemSchema,
                DiffType.ARRAY_TYPE_ITEM_SCHEMA_ADDED, DiffType.ARRAY_TYPE_ITEM_SCHEMA_REMOVED)) {
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
        if (DiffUtil.diffSubschemaAddedRemoved(subCtx, original.getContainedItemSchema(), containedItemSchema,
                DiffType.ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_ADDED, DiffType.ARRAY_TYPE_CONTAINED_ITEM_SCHEMA_REMOVED)) {
            containedItemSchema.accept(new SchemaDiffVisitor(subCtx, original.getContainedItemSchema()));
        }
        super.visitContainedItemSchema(containedItemSchema);
    }
}
