package io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ConditionalSchema;

import java.util.Optional;

@ToString
public class ConditionalSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ConditionalSchema wrapped;

    public ConditionalSchemaWrapper(ConditionalSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitConditionalSchema(this);
    }

    public Optional<SchemaWrapper> getIfSchema() {
        return WrapUtil.wrap(wrapped.getIfSchema());
    }

    public Optional<SchemaWrapper> getThenSchema() {
        return WrapUtil.wrap(wrapped.getThenSchema());
    }

    public Optional<SchemaWrapper> getElseSchema() {
        return WrapUtil.wrap(wrapped.getElseSchema());
    }
}
