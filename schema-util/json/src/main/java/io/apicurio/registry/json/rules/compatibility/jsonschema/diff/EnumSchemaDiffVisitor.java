package io.apicurio.registry.json.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.EnumSchemaWrapper;
import org.everit.json.schema.EnumSchema;

import java.util.Set;

public class EnumSchemaDiffVisitor extends JsonSchemaWrapperVisitor {

    private final DiffContext ctx;
    private final EnumSchema original;

    public EnumSchemaDiffVisitor(DiffContext ctx, EnumSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitEnumSchema(EnumSchemaWrapper schema) {
        super.visitEnumSchema(schema);
    }

    @Override
    public void visitEnumValues(Set<Object> values) {
        DiffUtil.diffSetChanged(ctx.sub("enum"), original.getPossibleValues(), values, DiffType.UNDEFINED_UNUSED,
                DiffType.UNDEFINED_UNUSED, DiffType.ENUM_TYPE_VALUES_CHANGED, DiffType.ENUM_TYPE_VALUES_MEMBER_ADDED,
                DiffType.ENUM_TYPE_VALUES_MEMBER_REMOVED);
    }
}
