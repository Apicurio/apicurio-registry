package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.NullSchemaWrapper;
import org.everit.json.schema.NullSchema;


@SuppressWarnings("unused")
public class NullSchemaDiffVisitor extends JsonSchemaWrapperVisitor {

    private final DiffContext ctx;
    private final NullSchema original;

    public NullSchemaDiffVisitor(DiffContext ctx, NullSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitNullSchema(NullSchemaWrapper schema) {
        super.visitNullSchema(schema);
    }
}
