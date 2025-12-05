package io.apicurio.registry.json.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.ConditionalSchemaWrapper;
import io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.ConditionalSchema;
import org.everit.json.schema.Schema;

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
        DiffUtil.compareSchema(ctx.sub("if"), o, ifSchema.getWrapped(), DiffType.CONDITIONAL_TYPE_IF_SCHEMA_ADDED,
                DiffType.CONDITIONAL_TYPE_IF_SCHEMA_REMOVED, DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BOTH,
                DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
                DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
                DiffType.CONDITIONAL_TYPE_IF_SCHEMA_COMPATIBLE_NONE);
        super.visitIfSchema(ifSchema);
    }

    @Override
    public void visitThenSchema(SchemaWrapper thenSchema) {
        Schema o = original.getThenSchema().orElse(null);
        DiffUtil.compareSchema(ctx.sub("then"), o, thenSchema.getWrapped(), DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_ADDED,
                DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_REMOVED, DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BOTH,
                DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
                DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
                DiffType.CONDITIONAL_TYPE_THEN_SCHEMA_COMPATIBLE_NONE);
        super.visitThenSchema(thenSchema);
    }

    @Override
    public void visitElseSchema(SchemaWrapper elseSchema) {
        Schema o = original.getElseSchema().orElse(null);
        DiffUtil.compareSchema(ctx.sub("else"), o, elseSchema.getWrapped(), DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_ADDED,
                DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_REMOVED, DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BOTH,
                DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
                DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
                DiffType.CONDITIONAL_TYPE_ELSE_SCHEMA_COMPATIBLE_NONE);
        super.visitElseSchema(elseSchema);
    }
}
