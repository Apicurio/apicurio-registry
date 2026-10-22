package io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.NotSchema;

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
        return WrapUtil.wrap(wrapped.getMustNotMatch());
    }
}
