package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ArraySchema;

import java.util.List;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;


@ToString
public class ArraySchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ArraySchema wrapped;

    public ArraySchemaWrapper(ArraySchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public Integer getMinItems() {
        return wrapped.getMinItems();
    }

    public Integer getMaxItems() {
        return wrapped.getMaxItems();
    }

    public boolean needsUniqueItems() {
        return wrapped.needsUniqueItems();
    }

    public SchemaWrapper getAllItemSchema() {
        return wrap(wrapped.getAllItemSchema());
    }

    public boolean permitsAdditionalItems() {
        return wrapped.permitsAdditionalItems();
    }

    public List<SchemaWrapper> getItemSchemas() {
        return wrap(wrapped.getItemSchemas());
    }

    public SchemaWrapper getSchemaOfAdditionalItems() {
        return wrap(wrapped.getSchemaOfAdditionalItems());
    }

    public SchemaWrapper getContainedItemSchema() {
        return wrap(wrapped.getContainedItemSchema());
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitArraySchema(this);
    }
}
