package io.apicurio.registry.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.types.ContentTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static io.apicurio.registry.util.YAMLObjectMapper.YAML_MAPPER;

/**
 * Service that extracts embedded JSON Schema objects from MODEL_SCHEMA and PROMPT_TEMPLATE
 * artifacts and auto-registers them as separate JSON artifacts with $ref references.
 *
 * For MODEL_SCHEMA: extracts the "input" and "output" fields (which are full JSON Schema objects)
 * and registers them as separate JSON artifacts.
 *
 * For PROMPT_TEMPLATE: extracts the "outputSchema" field (which is a JSON Schema object)
 * and registers it as a separate JSON artifact.
 *
 * After extraction, the original content is rewritten to replace the embedded schemas with
 * $ref references pointing to the newly created artifacts.
 */
@ApplicationScoped
public class EmbeddedSchemaService {

    @Inject
    Logger log;

    /**
     * Result of extracting embedded schemas from an artifact.
     */
    public static class ExtractionResult {
        private final ContentHandle modifiedContent;
        private final String contentType;
        private final List<ArtifactReferenceDto> references;

        public ExtractionResult(ContentHandle modifiedContent, String contentType, List<ArtifactReferenceDto> references) {
            this.modifiedContent = modifiedContent;
            this.contentType = contentType;
            this.references = references;
        }

        public ContentHandle getModifiedContent() {
            return modifiedContent;
        }

        public String getContentType() {
            return contentType;
        }

        public List<ArtifactReferenceDto> getReferences() {
            return references;
        }
    }

    /**
     * Extract embedded schemas from a MODEL_SCHEMA artifact, auto-register them, and return
     * the modified content with $ref references.
     *
     * @param storage    the registry storage
     * @param groupId    the group ID where the artifact is being created
     * @param artifactId the artifact ID of the MODEL_SCHEMA being created
     * @param content    the raw content of the MODEL_SCHEMA
     * @param contentType the content type (application/json or application/x-yaml)
     * @param owner      the owner of the created artifacts
     * @return extraction result with modified content and references, or null if no extraction was performed
     */
    public ExtractionResult extractModelSchemaEmbeddedSchemas(RegistryStorage storage, String groupId,
            String artifactId, ContentHandle content, String contentType, String owner) {
        try {
            JsonNode root = parseContent(content.content(), contentType);
            if (root == null || !root.isObject()) {
                return null;
            }

            ObjectNode rootObj = (ObjectNode) root;
            List<ArtifactReferenceDto> references = new ArrayList<>();
            boolean modified = false;

            // Extract "input" schema
            if (rootObj.has("input") && rootObj.get("input").isObject() && hasSchemaProperties(rootObj.get("input"))) {
                String schemaArtifactId = artifactId + "-input-schema";
                String version = "1";
                String refName = buildReferenceName(groupId, schemaArtifactId, version);
                JsonNode inputSchema = rootObj.get("input");

                if (autoRegisterSchema(storage, groupId, schemaArtifactId, inputSchema,
                        "Input schema for " + artifactId, owner)) {
                    rootObj.set("input", createRefNode(refName));
                    references.add(ArtifactReferenceDto.builder()
                            .groupId(groupId).artifactId(schemaArtifactId).version(version).name(refName)
                            .build());
                    modified = true;
                }
            }

            // Extract "output" schema
            if (rootObj.has("output") && rootObj.get("output").isObject() && hasSchemaProperties(rootObj.get("output"))) {
                String schemaArtifactId = artifactId + "-output-schema";
                String version = "1";
                String refName = buildReferenceName(groupId, schemaArtifactId, version);
                JsonNode outputSchema = rootObj.get("output");

                if (autoRegisterSchema(storage, groupId, schemaArtifactId, outputSchema,
                        "Output schema for " + artifactId, owner)) {
                    rootObj.set("output", createRefNode(refName));
                    references.add(ArtifactReferenceDto.builder()
                            .groupId(groupId).artifactId(schemaArtifactId).version(version).name(refName)
                            .build());
                    modified = true;
                }
            }

            if (!modified) {
                return null;
            }

            // Serialize back to original format
            String modifiedContentStr = serializeContent(rootObj, contentType);
            return new ExtractionResult(
                    ContentHandle.create(modifiedContentStr),
                    contentType,
                    references
            );
        } catch (Exception e) {
            log.warn("Failed to extract embedded schemas from MODEL_SCHEMA artifact: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract embedded schemas from a PROMPT_TEMPLATE artifact, auto-register them, and return
     * the modified content with $ref references.
     */
    public ExtractionResult extractPromptTemplateEmbeddedSchemas(RegistryStorage storage, String groupId,
            String artifactId, ContentHandle content, String contentType, String owner) {
        try {
            JsonNode root = parseContent(content.content(), contentType);
            if (root == null || !root.isObject()) {
                return null;
            }

            ObjectNode rootObj = (ObjectNode) root;
            List<ArtifactReferenceDto> references = new ArrayList<>();
            boolean modified = false;

            // Extract "outputSchema"
            if (rootObj.has("outputSchema") && rootObj.get("outputSchema").isObject() && hasSchemaProperties(rootObj.get("outputSchema"))) {
                String schemaArtifactId = artifactId + "-output-schema";
                String version = "1";
                String refName = buildReferenceName(groupId, schemaArtifactId, version);
                JsonNode outputSchema = rootObj.get("outputSchema");

                if (autoRegisterSchema(storage, groupId, schemaArtifactId, outputSchema,
                        "Output schema for prompt template " + artifactId, owner)) {
                    rootObj.set("outputSchema", createRefNode(refName));
                    references.add(ArtifactReferenceDto.builder()
                            .groupId(groupId).artifactId(schemaArtifactId).version(version).name(refName)
                            .build());
                    modified = true;
                }
            }

            if (!modified) {
                return null;
            }

            String modifiedContentStr = serializeContent(rootObj, contentType);
            return new ExtractionResult(
                    ContentHandle.create(modifiedContentStr),
                    contentType,
                    references
            );
        } catch (Exception e) {
            log.warn("Failed to extract embedded schemas from PROMPT_TEMPLATE artifact: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Auto-register a JSON Schema as a separate JSON artifact.
     *
     * @return true if the schema was successfully registered, false otherwise
     */
    private boolean autoRegisterSchema(RegistryStorage storage, String groupId, String schemaArtifactId,
            JsonNode schema, String description, String owner) {
        try {
            String schemaContent = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
            ContentHandle contentHandle = ContentHandle.create(schemaContent);

            EditableArtifactMetaDataDto artifactMeta = EditableArtifactMetaDataDto.builder()
                    .name(schemaArtifactId)
                    .description(description)
                    .build();
            EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder()
                    .name(schemaArtifactId)
                    .description(description)
                    .build();
            ContentWrapperDto contentWrapper = ContentWrapperDto.builder()
                    .content(contentHandle)
                    .contentType(ContentTypes.APPLICATION_JSON)
                    .references(Collections.emptyList())
                    .build();

            storage.createArtifact(groupId, schemaArtifactId, "JSON", artifactMeta,
                    "1", contentWrapper, versionMeta, Collections.emptyList(),
                    false, false, owner);

            log.info("Auto-registered embedded schema as artifact: {}/{}", groupId, schemaArtifactId);
            return true;
        } catch (ArtifactAlreadyExistsException e) {
            log.info("Embedded schema artifact already exists: {}/{}, skipping auto-registration",
                    groupId, schemaArtifactId);
            return false;
        } catch (Exception e) {
            log.warn("Failed to auto-register embedded schema {}/{}: {}", groupId, schemaArtifactId, e.getMessage());
            return false;
        }
    }

    private JsonNode parseContent(String content, String contentType) throws JsonProcessingException {
        if (contentType != null && (contentType.contains("yaml") || contentType.equals("text/x-prompt-template"))) {
            return YAML_MAPPER.readTree(content);
        }
        try {
            return MAPPER.readTree(content);
        } catch (JsonProcessingException e) {
            // Try YAML as fallback
            return YAML_MAPPER.readTree(content);
        }
    }

    private String serializeContent(JsonNode node, String contentType) throws JsonProcessingException {
        if (contentType != null && (contentType.contains("yaml") || contentType.equals("text/x-prompt-template"))) {
            return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    /**
     * Check if a JSON node looks like a JSON Schema (has "type", "properties", "items", etc.)
     * rather than just a $ref or a primitive value.
     */
    private boolean hasSchemaProperties(JsonNode node) {
        if (!node.isObject()) return false;
        // If it already has a $ref, don't extract it
        if (node.has("$ref")) return false;
        // Must have at least one schema-like field
        return node.has("type") || node.has("properties") || node.has("items")
                || node.has("oneOf") || node.has("anyOf") || node.has("allOf");
    }

    /**
     * Build a fully-qualified reference name following Apicurio Registry conventions.
     * Format: {groupId}:{artifactId}:{version}
     * This makes references self-describing and resolvable by consumers like the render service.
     */
    private String buildReferenceName(String groupId, String artifactId, String version) {
        String effectiveGroupId = groupId != null ? groupId : "default";
        return effectiveGroupId + ":" + artifactId + ":" + version;
    }

    private ObjectNode createRefNode(String refName) {
        ObjectNode refNode = MAPPER.createObjectNode();
        refNode.put("$ref", refName);
        return refNode;
    }
}
