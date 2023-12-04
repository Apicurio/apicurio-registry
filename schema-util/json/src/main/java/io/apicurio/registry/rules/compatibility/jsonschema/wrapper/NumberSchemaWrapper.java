package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.NumberSchema;


@ToString
public class NumberSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final NumberSchema wrapped;

    public NumberSchemaWrapper(NumberSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitNumberSchema(this);
    }

    public boolean isExclusiveMinimum() {
        return wrapped.isExclusiveMinimum();
    }

    public Number getMinimum() {
        return wrapped.getMinimum();
    }

    public Number getExclusiveMinimumLimit() {
        return wrapped.getExclusiveMinimumLimit();
    }

    public boolean isExclusiveMaximum() {
        return wrapped.isExclusiveMaximum();
    }

    public Number getMaximum() {
        return wrapped.getMaximum();
    }

    public Number getExclusiveMaximumLimit() {
        return wrapped.getExclusiveMaximumLimit();
    }

    public Number getMultipleOf() {
        return wrapped.getMultipleOf();
    }

    public boolean requiresInteger() {
        return wrapped.requiresInteger();
    }
}
