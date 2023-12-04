package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.NullSchema;


@ToString
public class NullSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final NullSchema wrapped;

    public NullSchemaWrapper(NullSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitNullSchema(this);
    }
}
