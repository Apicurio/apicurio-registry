package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.NotSchema;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;

@ToString
public class NotSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final NotSchema wrapped;

    public NotSchemaWrapper(NotSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitNotSchema(this);
    }

    public SchemaWrapper getMustNotMatch() {
        return wrap(wrapped.getMustNotMatch());
    }
}
