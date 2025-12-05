package io.apicurio.registry.json.rules.compatibility.jsonschema.wrapper;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonUtil;
import lombok.Getter;
import org.everit.json.schema.Schema;
import org.everit.json.schema.internal.JSONPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Equals and hashCode implementation from the Everit library may not handle some schemas (with references)
 * well, resulting in {@link StackOverflowError} or other errors. When using collections, always wrap the
 * schema inside this wrapper, or any other wrapper that inherits from this one.
 */
// TODO Should implement SchemaWrapper?
public class EqualitySchemaWrapper implements SchemaWrapper {

    @Getter
    private final Schema wrapped;

    public EqualitySchemaWrapper(Schema wrapped) {
        Objects.requireNonNull(wrapped);
        this.wrapped = wrapped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        EqualitySchemaWrapper that = (EqualitySchemaWrapper) o;

        try (StringWriter writer1 = new StringWriter(); StringWriter writer2 = new StringWriter()) {
            wrapped.describeTo(new JSONPrinter(writer1));
            JsonNode thisWrappedNode = JsonUtil.MAPPER.readTree(writer1.toString());
            that.wrapped.describeTo(new JSONPrinter(writer2));
            JsonNode thatWrappedNode = JsonUtil.MAPPER.readTree(writer2.toString());

            return thisWrappedNode.equals(thatWrappedNode);
        } catch (IOException ex) {
            throw new RuntimeException(
                    "Could not perform equality comparison on this " + this + " and that " + that, ex);
        }
    }

    @Override
    public int hashCode() {
        try (StringWriter writer = new StringWriter()) {
            wrapped.describeTo(new JSONPrinter(writer));
            JsonNode wrappedNode = JsonUtil.MAPPER.readTree(writer.toString());

            return wrappedNode.hashCode();
        } catch (IOException ex) {
            throw new RuntimeException("Could not perform hashCode computation on " + this, ex);
        }
    }
}
