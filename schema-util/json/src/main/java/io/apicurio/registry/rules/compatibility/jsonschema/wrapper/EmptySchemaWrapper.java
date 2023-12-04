package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.EmptySchema;


@ToString
public class EmptySchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    protected final EmptySchema wrapped;

    public EmptySchemaWrapper(EmptySchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitEmptySchema(this);
    }
}
