package io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.SchemaLocation;

@ToString
public class ReferenceSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ReferenceSchema wrapped;

    public ReferenceSchemaWrapper(ReferenceSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitReferenceSchema(this);
    }

    public SchemaLocation getLocation() {
        return wrapped.getLocation();
    }

    public SchemaWrapper getReferredSchema() {
        return WrapUtil.wrap(wrapped.getReferredSchema());
    }
}
