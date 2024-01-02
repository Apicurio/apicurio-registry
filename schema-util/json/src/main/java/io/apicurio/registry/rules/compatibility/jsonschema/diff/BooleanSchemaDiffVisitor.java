package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import io.apicurio.registry.rules.compatibility.jsonschema.wrapper.BooleanSchemaWrapper;
import org.everit.json.schema.BooleanSchema;

@SuppressWarnings("unused")
public class BooleanSchemaDiffVisitor extends JsonSchemaWrapperVisitor {

    private final DiffContext ctx;
    private final BooleanSchema original;

    public BooleanSchemaDiffVisitor(DiffContext ctx, BooleanSchema original) {
        this.ctx = ctx;
        this.original = original;
    }

    @Override
    public void visitBooleanSchema(BooleanSchemaWrapper schema) {
        super.visitBooleanSchema(schema);
    }
}
