package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ConstSchema;


@ToString
public class ConstSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ConstSchema wrapped;

    public ConstSchemaWrapper(ConstSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitConstSchema(this);
    }

    public Object getPermittedValue() {
        return wrapped.getPermittedValue();
    }
}
