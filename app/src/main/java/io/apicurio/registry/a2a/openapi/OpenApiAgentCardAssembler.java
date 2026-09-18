package io.apicurio.registry.a2a.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.validity.AgentCardContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;

/**
 * Extracts the {@code x-agent-card} vendor extension from an OpenAPI document's {@code info} block,
 * derives any fields that can be inferred from the surrounding OpenAPI document (name, description,
 * version, and {@code supportedInterfaces} from {@code servers[]}), and validates the assembled
 * result against the A2A v1.0 Agent Card schema.
 *
 * <p>This class is a plain, side-effect-free assembler: it never touches storage. Callers are
 * expected to catch {@link RuleViolationException} and translate it into whatever error response is
 * appropriate for their context (this is the same exception type thrown by content validators
 * elsewhere in the registry, so it is already understood by the existing REST exception mapping).
 */
public class OpenApiAgentCardAssembler {

    /**
     * The name of the OpenAPI {@code info} vendor extension recognized by this assembler.
     */
    public static final String EXTENSION_KEY = "x-agent-card";

    private static final String DEFAULT_PROTOCOL_BINDING = "http+json";
    private static final String DEFAULT_PROTOCOL_VERSION = "1.0";

    private final AgentCardContentValidator agentCardValidator = new AgentCardContentValidator();

    /**
     * Assembles a v1.0-conformant Agent Card JSON document from the {@code x-agent-card} extension
     * embedded in the given OpenAPI content, if present.
     *
     * @param openApiContent the OpenAPI document (JSON or YAML)
     * @return the assembled Agent Card as a JSON string, or {@code null} if the OpenAPI document has
     *         no {@code x-agent-card} extension under {@code info}
     * @throws IOException             if the OpenAPI content cannot be parsed as JSON or YAML
     * @throws RuleViolationException  if the extension is present but the assembled Agent Card is
     *                                  not a valid A2A v1.0 Agent Card
     */
    public String assemble(TypedContent openApiContent) throws IOException {
        JsonNode root = ContentTypeUtil.parseJsonOrYaml(openApiContent);
        JsonNode infoNode = root.path("info");
        JsonNode extensionNode = infoNode.path(EXTENSION_KEY);

        if (extensionNode.isMissingNode() || extensionNode.isNull()) {
            return null;
        }

        if (!extensionNode.isObject()) {
            throw new RuleViolationException("The 'x-agent-card' extension must be a JSON object",
                    RuleType.VALIDITY, ValidityLevel.FULL.name(), Collections.singleton(
                            new RuleViolation("'x-agent-card' must be a JSON object", "/info/x-agent-card")));
        }

        ObjectNode agentCard = ((ObjectNode) extensionNode).deepCopy();

        deriveMissingField(agentCard, "name", infoNode.path("title"));
        deriveMissingField(agentCard, "description", infoNode.path("description"));
        deriveMissingField(agentCard, "version", infoNode.path("version"));
        deriveInterfacesFromServers(agentCard, root.path("servers"));

        String assembledJson = MAPPER.writeValueAsString(agentCard);
        validateAssembled(assembledJson);
        return assembledJson;
    }

    /**
     * Sets {@code fieldName} on {@code target} from {@code fallback} only when it is not already
     * present as a non-blank string on {@code target}. Leaves {@code target} unchanged if
     * {@code fallback} is not a usable string either.
     */
    private void deriveMissingField(ObjectNode target, String fieldName, JsonNode fallback) {
        JsonNode existing = target.path(fieldName);
        boolean present = existing.isTextual() && !existing.asText().isBlank();
        if (!present && fallback.isTextual() && !fallback.asText().isBlank()) {
            target.put(fieldName, fallback.asText());
        }
    }

    /**
     * Populates {@code supportedInterfaces} on {@code agentCard} from the OpenAPI document's
     * {@code servers[]} array, but only when the extension did not already supply a non-empty
     * {@code supportedInterfaces} array itself. Each derived interface uses
     * {@value #DEFAULT_PROTOCOL_BINDING} as its protocol binding and either the agent card's own
     * {@code protocolVersion} (if supplied) or {@value #DEFAULT_PROTOCOL_VERSION}.
     */
    private void deriveInterfacesFromServers(ObjectNode agentCard, JsonNode servers) {
        JsonNode existing = agentCard.path("supportedInterfaces");
        if (existing.isArray() && !existing.isEmpty()) {
            return;
        }
        if (!servers.isArray() || servers.isEmpty()) {
            return;
        }

        String protocolVersion = agentCard.path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
        ArrayNode interfaces = MAPPER.createArrayNode();
        for (JsonNode server : servers) {
            JsonNode urlNode = server.path("url");
            if (!urlNode.isTextual() || urlNode.asText().isBlank()) {
                continue;
            }
            ObjectNode iface = MAPPER.createObjectNode();
            iface.put("url", urlNode.asText());
            iface.put("protocolBinding", DEFAULT_PROTOCOL_BINDING);
            iface.put("protocolVersion", protocolVersion);
            interfaces.add(iface);
        }
        if (!interfaces.isEmpty()) {
            agentCard.set("supportedInterfaces", interfaces);
        }
    }

    /**
     * Validates the assembled Agent Card JSON against the A2A v1.0 schema, remapping any violation
     * contexts to be relative to the OpenAPI document (e.g. {@code /skills/0/tags} becomes
     * {@code /info/x-agent-card/skills/0/tags}) so error messages point at the right place in the
     * artifact the caller actually submitted.
     */
    private void validateAssembled(String assembledJson) {
        TypedContent agentCardContent = TypedContent.create(assembledJson, ContentTypes.APPLICATION_JSON);
        try {
            agentCardValidator.validate(ValidityLevel.FULL, agentCardContent, Collections.emptyMap());
        } catch (RuleViolationException e) {
            Set<RuleViolation> remapped = e.getCauses().stream()
                    .map(v -> new RuleViolation(v.getDescription(), "/info/x-agent-card" + v.getContext()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw new RuleViolationException(
                    "The 'x-agent-card' extension does not assemble into a valid A2A v1.0 Agent Card",
                    RuleType.VALIDITY, ValidityLevel.FULL.name(), remapped);
        }
    }
}
