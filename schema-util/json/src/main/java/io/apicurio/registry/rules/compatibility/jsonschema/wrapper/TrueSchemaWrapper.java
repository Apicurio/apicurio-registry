package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.TrueSchema;

@ToString
public class TrueSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final TrueSchema wrapped;

    public TrueSchemaWrapper(TrueSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitTrueSchema(this);
    }
}
