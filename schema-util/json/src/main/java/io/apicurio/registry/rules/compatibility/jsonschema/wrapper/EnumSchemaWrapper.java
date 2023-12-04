package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.EnumSchema;

import java.util.Set;


@ToString
public class EnumSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final EnumSchema wrapped;

    public EnumSchemaWrapper(EnumSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitEnumSchema(this);
    }

    public Set<Object> getPossibleValues() {
        return wrapped.getPossibleValues();
    }
}
