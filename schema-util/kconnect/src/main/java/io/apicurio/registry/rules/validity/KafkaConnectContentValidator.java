package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A content validator implementation for the Kafka Connect schema content type.
 */
public class KafkaConnectContentValidator implements ContentValidator {

    private static final ObjectMapper mapper;
    private static final JsonConverter jsonConverter;
    static {
        mapper = new ObjectMapper();
        jsonConverter = new JsonConverter();
        Map<String, Object> configs = new HashMap<>();
        configs.put("converter.type", "key");
        configs.put(JsonConverterConfig.SCHEMAS_CACHE_SIZE_CONFIG, 0);
        jsonConverter.configure(configs);
    }

    /**
     * Constructor.
     */
    public KafkaConnectContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try {
                JsonNode jsonNode = mapper.readTree(content.getContent().content());
                jsonConverter.asConnectSchema(jsonNode);
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for Kafka Connect Schema artifact.", RuleType.VALIDITY, level.name(), e);
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) throws RuleViolationException {
        // Note: not yet implemented!
    }

}
