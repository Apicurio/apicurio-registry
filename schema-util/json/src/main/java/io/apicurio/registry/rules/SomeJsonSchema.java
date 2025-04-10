package io.apicurio.registry.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.erosb.jsonsKema.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import static io.apicurio.registry.rules.validity.JsonSchemaVersion.DRAFT_2020_12;
import static java.util.Objects.requireNonNull;

/**
 * Wraps a parsed JSON schema, that can be represented by exactly one of two different implementations.
 */
@ToString
@EqualsAndHashCode
public class SomeJsonSchema {

    @Getter
    private org.everit.json.schema.Schema everit;

    @Getter
    private com.github.erosb.jsonsKema.Schema jsonsKema;

    // I could not find a way to turn the Schema back into JSON for id field extraction, so we have to keep this :(
    @Getter
    private JsonNode jsonsKemaRawJsonNode;

    public SomeJsonSchema(org.everit.json.schema.Schema everit) {
        requireNonNull(everit);
        this.everit = everit;
    }

    public SomeJsonSchema(Schema jsonsKema, JsonNode jsonsKemaRawJsonNode) {
        requireNonNull(jsonsKema);
        requireNonNull(jsonsKemaRawJsonNode);
        this.jsonsKema = jsonsKema;
        this.jsonsKemaRawJsonNode = jsonsKemaRawJsonNode;
    }

    public String getId() {
        if (jsonsKema != null) {
            var idNode = jsonsKemaRawJsonNode.get(DRAFT_2020_12.getIdKeyword());
            if (idNode != null && idNode.isTextual()) {
                return idNode.textValue();
            } else {
                return null;
            }
        } else {
            return everit.getId();
        }
    }
}
