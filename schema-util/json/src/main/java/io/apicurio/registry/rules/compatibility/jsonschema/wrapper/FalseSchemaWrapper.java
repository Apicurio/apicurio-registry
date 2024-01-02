package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.FalseSchema;

@ToString
public class FalseSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final FalseSchema wrapped;

    public FalseSchemaWrapper(FalseSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitFalseSchema(this);
    }
}
