package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.RuleType;
import org.apache.avro.Schema;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A content validator implementation for the Avro content type.
 */
public class AvroContentValidator implements ContentValidator {
    
    private static final String DUMMY_AVRO_RECORD = """
            {
                 "type": "record",
                 "namespace": "NAMESPACE",
                 "name": "NAME",
                 "fields": [
                   { "name": "first", "type": "string" },
                   { "name": "last", "type": "string" }
                 ]
            }""";

    /**
     * Constructor.
     */
    public AvroContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                Schema.Parser parser = new Schema.Parser();
                for (TypedContent schemaTC : resolvedReferences.values()) {
                    parser.parse(schemaTC.getContent().content());
                }
                parser.parse(content.getContent().content());
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Avro artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }
    
    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
        try {
            Schema.Parser parser = new Schema.Parser();
            references.forEach(ref -> {
                String refName = ref.getName();
                if (refName != null && refName.contains(".")) {
                    int idx = refName.lastIndexOf('.');
                    String ns = refName.substring(0, idx);
                    String name = refName.substring(idx+1);
                    parser.parse(DUMMY_AVRO_RECORD.replace("NAMESPACE", ns).replace("NAME", name));
                }
            });
            parser.parse(content.getContent().content());
        } catch (Exception e) {
            // This is terrible, but I don't know how else to detect if the reason for the parse failure
            // is because of a missing defined type or some OTHER parse exception.
            if (e.getMessage().contains("is not a defined name")) {
                RuleViolation violation = new RuleViolation("Missing reference detected.", e.getMessage());
                throw new RuleViolationException("Missing reference detected in Avro artifact.", RuleType.INTEGRITY, 
                        IntegrityLevel.ALL_REFS_MAPPED.name(), Collections.singleton(violation));
            }
        }
    }

}
