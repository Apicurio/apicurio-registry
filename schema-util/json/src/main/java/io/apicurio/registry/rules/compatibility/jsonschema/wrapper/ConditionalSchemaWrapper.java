package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ConditionalSchema;

import java.util.Optional;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;


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
        return wrap(wrapped.getIfSchema());
    }

    public Optional<SchemaWrapper> getThenSchema() {
        return wrap(wrapped.getThenSchema());
    }

    public Optional<SchemaWrapper> getElseSchema() {
        return wrap(wrapped.getElseSchema());
    }
}
