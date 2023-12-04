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
