package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.CombinedSchema;
import org.everit.json.schema.CombinedSchema.ValidationCriterion;

import java.util.Collection;

@ToString
public class CombinedSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final CombinedSchema wrapped;

    public CombinedSchemaWrapper(CombinedSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitCombinedSchema(this);
    }

    public ValidationCriterion getCriterion() {
        return wrapped.getCriterion();
    }

    public Collection<SchemaWrapper> getSubschemas() {
        return WrapUtil.wrap(wrapped.getSubschemas());
    }
}
