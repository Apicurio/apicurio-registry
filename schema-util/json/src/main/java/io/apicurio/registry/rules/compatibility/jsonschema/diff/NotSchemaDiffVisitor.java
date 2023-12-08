package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NotSchemaWrapper;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.SchemaWrapper;
import org.everit.json.schema.NotSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_COMPATIBLE_BOTH;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.NOT_TYPE_SCHEMA_COMPATIBLE_NONE;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffType.UNDEFINED_UNUSED;
import static io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffUtil.compareSchema;

public class NotSchemaDiffVisitor extends JsonSchemaWrapperVisitor {


    private DiffContext ctx;
    private final NotSchema original;

    public NotSchemaDiffVisitor(DiffContext ctx, NotSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    public void visitNotSchema(NotSchemaWrapper notSchema) {
        super.visitNotSchema(notSchema);
    }

    public void visitSchemaMustNotMatch(SchemaWrapper mustNotMatch) {
        compareSchema(ctx.sub("not"), original.getMustNotMatch(), mustNotMatch.getWrapped(),
            UNDEFINED_UNUSED,
            UNDEFINED_UNUSED,
            NOT_TYPE_SCHEMA_COMPATIBLE_BOTH,
            NOT_TYPE_SCHEMA_COMPATIBLE_BACKWARD_NOT_FORWARD,
            NOT_TYPE_SCHEMA_COMPATIBLE_FORWARD_NOT_BACKWARD,
            NOT_TYPE_SCHEMA_COMPATIBLE_NONE);
        super.visitSchemaMustNotMatch(mustNotMatch);
    }
}
