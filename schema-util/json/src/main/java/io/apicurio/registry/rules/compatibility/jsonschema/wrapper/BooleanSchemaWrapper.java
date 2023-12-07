package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.BooleanSchema;

@ToString
public class BooleanSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final BooleanSchema wrapped;

    public BooleanSchemaWrapper(BooleanSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitBooleanSchema(this);
    }
}
