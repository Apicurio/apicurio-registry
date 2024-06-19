package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.ConstSchemaWrapper;
import org.everit.json.schema.ConstSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.CONST_TYPE_VALUE_CHANGED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.diffObject;

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
        diffObject(ctx.sub("const"), original.getPermittedValue(), value, UNDEFINED_UNUSED, UNDEFINED_UNUSED,
                CONST_TYPE_VALUE_CHANGED);
        super.visitConstValue(value);
    }
}
