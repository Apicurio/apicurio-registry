package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.ObjectSchema;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.apicurio.registry.rules.compatibility.jsonschema.wrapper.WrapUtil.wrap;


@ToString
public class ObjectSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final ObjectSchema wrapped;

    public ObjectSchemaWrapper(ObjectSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public List<String> getRequiredProperties() {
        return wrapped.getRequiredProperties();
    }

    public SchemaWrapper getPropertyNameSchema() {
        return wrap(wrapped.getPropertyNameSchema());
    }

    public Integer getMinProperties() {
        return wrapped.getMinProperties();
    }

    public Integer getMaxProperties() {
        return wrapped.getMaxProperties();
    }

    public Map<String, Set<String>> getPropertyDependencies() {
        return wrapped.getPropertyDependencies();
    }

    public boolean permitsAdditionalProperties() {
        return wrapped.permitsAdditionalProperties();
    }

    public SchemaWrapper getSchemaOfAdditionalProperties() {
        return wrap(wrapped.getSchemaOfAdditionalProperties());
    }

    @SuppressWarnings("deprecation")
    public Map<Pattern, SchemaWrapper> getRegexpPatternProperties() {
        return wrap(wrapped.getPatternProperties()); // TODO Possible deprecation issue
    }


    public Map<String, SchemaWrapper> getSchemaDependencies() {
        return wrap(wrapped.getSchemaDependencies());
    }

    public Map<String, SchemaWrapper> getPropertySchemas() {
        return wrap(wrapped.getPropertySchemas());
    }


    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitObjectSchema(this);
    }
}
