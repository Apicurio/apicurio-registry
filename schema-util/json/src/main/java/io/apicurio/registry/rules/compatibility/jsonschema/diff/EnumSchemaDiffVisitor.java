package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.EnumSchemaWrapper;
import org.everit.json.schema.EnumSchema;

import java.util.Set;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ENUM_TYPE_VALUES_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ENUM_TYPE_VALUES_MEMBER_ADDED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.ENUM_TYPE_VALUES_MEMBER_REMOVED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffSetChanged;


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
        diffSetChanged(ctx.sub("enum"),
            original.getPossibleValues(),
            values,
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            ENUM_TYPE_VALUES_CHANGED,
            ENUM_TYPE_VALUES_MEMBER_ADDED,
            ENUM_TYPE_VALUES_MEMBER_REMOVED);
    }
}
