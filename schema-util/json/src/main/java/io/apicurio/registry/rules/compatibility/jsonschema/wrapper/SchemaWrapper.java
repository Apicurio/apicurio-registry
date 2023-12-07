package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import org.everit.json.schema.Schema;

public interface SchemaWrapper {

    default void accept(JsonSchemaWrapperVisitor visitor) {
        if (!(this instanceof Schema)) {
            throw new IllegalStateException();
        }
        visitor.visit(this);
    }

    Schema getWrapped();
}
