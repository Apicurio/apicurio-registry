package io.apicurio.registry.rules.compatibility.jsonschema.wrapper;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaWrapperVisitor;
import lombok.Getter;
import lombok.ToString;
import org.everit.json.schema.FormatValidator;
import org.everit.json.schema.StringSchema;

import java.util.Map;
import java.util.regex.Pattern;


@ToString
public class StringSchemaWrapper extends EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final StringSchema wrapped;

    public StringSchemaWrapper(StringSchema wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public Integer getMinLength() {
        return wrapped.getMinLength();
    }

    public Integer getMaxLength() {
        return wrapped.getMaxLength();
    }

    public FormatValidator getFormatValidator() {
        return wrapped.getFormatValidator();
    }

    public Pattern getPattern() {
        return wrapped.getPattern();
    }

    public Map<String, Object> getUnprocessedProperties() {
        return wrapped.getUnprocessedProperties();
    }

    @Override
    public void accept(JsonSchemaWrapperVisitor visitor) {
        visitor.visitStringSchema(this);
    }
}
