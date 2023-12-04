package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ReferenceSchema;
import org.everit.json.schema.SchemaLocation;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;


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
        return wrap(wrapped.getReferredSchema());
    }
}
