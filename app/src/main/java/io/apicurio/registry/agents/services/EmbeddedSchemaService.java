package io.apicurio.registry.agents.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.extensions.ArtifactVersionWriteHook;
import io.apicurio.registry.extensions.ContentPlan;
import io.apicurio.registry.extensions.VersionWriteContext;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p>
 * Registration is planned, not written, by {@link #planContent}: a schema that matches an existing
 * version is reused without writing anything; a new artifact or version is predicted (its coordinates
 * decided) and written only by the returned {@link ContentPlan#commit()}, which the caller runs
 * after rules and every other hook have passed, and never on a dry run. This keeps a later rejection
 * from leaving a registered schema behind. See issue #10603.
 * </p>
 */
@ApplicationScoped
public class EmbeddedSchemaService implements ArtifactVersionWriteHook {

    private static final String INPUT_FIELD = "input";

    @Inject
    Logger log;

    @Inject
    ResourceAccessGuard accessGuard;

    @Override
    public ContentPlan planContent(VersionWriteContext context, TypedContent content) {
        String contentType = content.getContentType();
        String groupId = context.getGa().getRawGroupIdWithNull();
        String artifactId = context.getGa().getRawArtifactId();
        // The extracted schemas are written as separate artifacts (created, or given a new version).
        // Check them all before planning any, with every check a direct write would get (owner-only
        // authorization and grants, and group write for a new one), so a forbidden target fails the
        // request without side effects instead of being written for a caller who may not write it.
        for (String target : embeddedSchemaArtifactIds(context.getArtifactType(), artifactId, content)) {
            accessGuard.requireGeneratedArtifactWrite(groupId, target, true);
        }
        if (ArtifactType.MODEL_SCHEMA.equals(context.getArtifactType())) {
            return planModelSchemaEmbeddedSchemas(context.getStorage(), groupId, artifactId,
                    content.getContent(), contentType, context.getOwner());
        }
        if (ArtifactType.PROMPT_TEMPLATE.equals(context.getArtifactType())) {
            return planPromptTemplateEmbeddedSchemas(context.getStorage(), groupId, artifactId,
                    content.getContent(), contentType, context.getOwner());
        }
        return null;
    }

    /**
     * @return the IDs of the artifacts the embedded schemas of this content would be registered as,
     *         using the same conditions as the extraction; empty if nothing would be extracted
     */
    List<String> embeddedSchemaArtifactIds(String artifactType, String artifactId, TypedContent content) {
        List<String> fields;
        if (ArtifactType.MODEL_SCHEMA.equals(artifactType)) {
            fields = List.of(INPUT_FIELD, "output");
        } else if (ArtifactType.PROMPT_TEMPLATE.equals(artifactType)) {
            fields = List.of("outputSchema");
        } else {
            return List.of();
        }
        JsonNode root;
        try {
            root = parseContent(content.getContent().content(), content.getContentType());
        } catch (Exception e) {
            // Unparseable content: nothing is extracted
            return List.of();
        }
        if (root == null || !root.isObject()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String field : fields) {
            if (root.has(field) && root.get(field).isObject() && hasSchemaProperties(root.get(field))) {
                ids.add(artifactId + (INPUT_FIELD.equals(field) ? "-input-schema" : "-output-schema"));
            }
        }
        return ids;
    }

    /**
     * Plans extraction of embedded schemas from a MODEL_SCHEMA artifact's "input" and "output" fields.
     *
     * @return the plan, or null if there is nothing to extract
     */
    ContentPlan planModelSchemaEmbeddedSchemas(RegistryStorage storage, String groupId, String artifactId,
            ContentHandle content, String contentType, String owner) {
        try {
            JsonNode root = parseContent(content.content(), contentType);
            if (root == null || !root.isObject()) {
                return null;
            }
            ObjectNode rootObj = (ObjectNode) root;
            PlanContext ctx = new PlanContext(storage, groupId, artifactId, owner);

            List<FieldPlan> fieldPlans = new ArrayList<>();
            planField(ctx, rootObj, INPUT_FIELD, "input-schema", "Input schema for " + artifactId, fieldPlans);
            planField(ctx, rootObj, "output", "output-schema", "Output schema for " + artifactId, fieldPlans);

            if (fieldPlans.isEmpty()) {
                return null;
            }
            return buildContentPlan(rootObj, contentType, fieldPlans);
        } catch (Exception e) {
            log.warn("Failed to plan embedded schema extraction from MODEL_SCHEMA artifact: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Plans extraction of the embedded schema from a PROMPT_TEMPLATE artifact's "outputSchema" field.
     *
     * @return the plan, or null if there is nothing to extract
     */
    ContentPlan planPromptTemplateEmbeddedSchemas(RegistryStorage storage, String groupId, String artifactId,
            ContentHandle content, String contentType, String owner) {
        try {
            JsonNode root = parseContent(content.content(), contentType);
            if (root == null || !root.isObject()) {
                return null;
            }
            ObjectNode rootObj = (ObjectNode) root;
            PlanContext ctx = new PlanContext(storage, groupId, artifactId, owner);

            List<FieldPlan> fieldPlans = new ArrayList<>();
            planField(ctx, rootObj, "outputSchema", "output-schema",
                    "Output schema for prompt template " + artifactId, fieldPlans);

            if (fieldPlans.isEmpty()) {
                return null;
            }
            return buildContentPlan(rootObj, contentType, fieldPlans);
        } catch (Exception e) {
            log.warn("Failed to plan embedded schema extraction from PROMPT_TEMPLATE artifact: {}", e.getMessage());
            return null;
        }
    }

    /**
     * If {@code field} holds an embeddable schema, plans its registration (reusing a matching existing
     * version, or predicting a new artifact or version) and rewrites {@code rootObj}'s field in place to
     * a {@code $ref}. Appends the plan to {@code fieldPlans} so the caller can build the content plan.
     */
    private void planField(PlanContext ctx, ObjectNode rootObj, String field, String suffix, String description,
            List<FieldPlan> fieldPlans) {
        if (!rootObj.has(field) || !rootObj.get(field).isObject() || !hasSchemaProperties(rootObj.get(field))) {
            return;
        }
        String schemaArtifactId = ctx.artifactId() + "-" + suffix;
        JsonNode schema = rootObj.get(field);
        SchemaRegistration registration = planSchemaRegistration(ctx, schemaArtifactId, schema, description);
        if (registration == null) {
            return;
        }
        String refName = buildReferenceName(ctx.groupId(), schemaArtifactId, registration.version());
        rootObj.set(field, createRefNode(refName));
        ArtifactReferenceDto reference = ArtifactReferenceDto.builder().groupId(ctx.groupId())
                .artifactId(schemaArtifactId).version(registration.version()).name(refName).build();
        fieldPlans.add(new FieldPlan(reference, registration));
    }

    private ContentPlan buildContentPlan(ObjectNode rootObj, String contentType, List<FieldPlan> fieldPlans)
            throws JsonProcessingException {
        String modifiedContentStr = serializeContent(rootObj, contentType);
        List<ArtifactReferenceDto> references = fieldPlans.stream().map(FieldPlan::reference).toList();
        ContentWrapperDto content = ContentWrapperDto.builder()
                .content(ContentHandle.create(modifiedContentStr))
                .contentType(contentType)
                .references(references)
                .build();

        Map<ArtifactReferenceDto, ContentWrapperDto> plannedReferencedContent = new HashMap<>();
        for (FieldPlan fieldPlan : fieldPlans) {
            if (fieldPlan.registration().write() != null) {
                // Reference resolution and rules must see the schema before it is committed; keyed
                // without a name, matching how the reference loader builds its lookup key.
                ArtifactReferenceDto key = ArtifactReferenceDto.builder()
                        .groupId(fieldPlan.reference().getGroupId()).artifactId(fieldPlan.reference().getArtifactId())
                        .version(fieldPlan.reference().getVersion()).build();
                plannedReferencedContent.put(key, fieldPlan.registration().write().content());
            }
        }

        return new ContentPlan(content, plannedReferencedContent, () -> commitFields(fieldPlans));
    }

    /**
     * Each field's write is independent (potentially a different artifact), so one field's write
     * succeeding does not make the whole plan atomic. If a later field's write fails (its predicted
     * coordinates were taken concurrently), undoes the earlier fields' writes rather than leaving them
     * registered with nothing referencing them: the parent is never written when this throws.
     */
    private void commitFields(List<FieldPlan> fieldPlans) {
        List<Runnable> committed = new ArrayList<>();
        try {
            for (FieldPlan fieldPlan : fieldPlans) {
                SchemaWrite write = fieldPlan.registration().write();
                if (write != null) {
                    write.commit().run();
                    committed.add(write.rollback());
                }
            }
        } catch (RuntimeException failure) {
            rollBack(committed);
            throw failure;
        }
    }

    /** Undoes already-committed writes in reverse order, best-effort: logs but does not throw. */
    private void rollBack(List<Runnable> committed) {
        for (int i = committed.size() - 1; i >= 0; i--) {
            try {
                committed.get(i).run();
            } catch (RuntimeException rollbackFailure) {
                log.warn("Failed to roll back an embedded schema registration after a later field's "
                        + "commit failed for the same write", rollbackFailure);
            }
        }
    }

    /**
     * Decides how a schema would be registered: reused from a matching existing version (nothing to
     * write), a new artifact (version {@code "1"}), or a new version of an existing artifact (predicted
     * from the artifact's current latest versionOrder, committed with
     * {@link RegistryStorage#createArtifactVersionIfLatest} so a concurrent write is detected instead of
     * silently registering under a different version than the one referenced).
     *
     * @return the registration, or null if nothing can be planned (matches the old behaviour of leaving
     *         the field inline and logging a warning)
     */
    private SchemaRegistration planSchemaRegistration(PlanContext ctx, String schemaArtifactId, JsonNode schema,
            String description) {
        RegistryStorage storage = ctx.storage();
        String groupId = ctx.groupId();
        String owner = ctx.owner();
        try {
            String schemaContent = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
            ContentHandle contentHandle = ContentHandle.create(schemaContent);
            ContentWrapperDto contentWrapper = ContentWrapperDto.builder().content(contentHandle)
                    .contentType(ContentTypes.APPLICATION_JSON).references(Collections.emptyList()).build();

            ArtifactVersionMetaDataDto latest;
            try {
                latest = latestVersion(storage, groupId, schemaArtifactId);
            } catch (ArtifactNotFoundException nfe) {
                // The schema artifact doesn't exist yet: predict a new artifact at version "1".
                EditableArtifactMetaDataDto artifactMeta = EditableArtifactMetaDataDto.builder()
                        .name(schemaArtifactId).description(description).build();
                EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder()
                        .name(schemaArtifactId).description(description).build();
                Runnable commit = () -> {
                    try {
                        storage.createArtifact(groupId, schemaArtifactId, "JSON", artifactMeta, "1",
                                contentWrapper, versionMeta, Collections.emptyList(), false, false, owner);
                        log.info("Registered embedded schema as a new artifact: {}/{}", groupId, schemaArtifactId);
                    } catch (ArtifactAlreadyExistsException ex) {
                        // Taken concurrently between planning and commit: fail the request rather than
                        // register a version the $ref in the parent doesn't match.
                        throw new VersionAlreadyExistsException(groupId, schemaArtifactId, "1");
                    }
                };
                Runnable rollback = () -> {
                    // Delete only the version this plan wrote: a concurrent write may have added
                    // another version to this artifact since, which must not be destroyed by undoing
                    // this plan. Remove the whole artifact only if it still holds just our version.
                    if (storage.getArtifactVersions(groupId, schemaArtifactId).size() == 1) {
                        storage.deleteArtifact(groupId, schemaArtifactId);
                    } else {
                        storage.deleteArtifactVersion(groupId, schemaArtifactId, "1");
                    }
                    log.info("Rolled back embedded schema artifact: {}/{}", groupId, schemaArtifactId);
                };
                return new SchemaRegistration("1", new SchemaWrite(contentWrapper, commit, rollback));
            }

            TypedContent typedContent = TypedContent.create(contentHandle, ContentTypes.APPLICATION_JSON);
            try {
                ArtifactVersionMetaDataDto existing = storage.getArtifactVersionMetaDataByContent(groupId,
                        schemaArtifactId, false, typedContent, Collections.emptyList());
                // Identical content already registered: nothing to write.
                return new SchemaRegistration(existing.getVersion(), null);
            } catch (ArtifactNotFoundException nfe) {
                // No version with matching content: predict the next one.
            }
            String predictedVersion = String.valueOf(latest.getVersionOrder() + 1);
            EditableVersionMetaDataDto versionMeta = EditableVersionMetaDataDto.builder().name(schemaArtifactId)
                    .description(description).build();
            int expectedBaseVersionOrder = latest.getVersionOrder();
            Runnable commit = () -> {
                try {
                    storage.createArtifactVersionIfLatest(groupId, schemaArtifactId, null, "JSON",
                            contentWrapper, versionMeta, Collections.emptyList(), false, owner,
                            expectedBaseVersionOrder, null);
                    log.info("Registered updated embedded schema as new version: {}/{} v{}", groupId,
                            schemaArtifactId, predictedVersion);
                } catch (CommitFailedException ex) {
                    // A concurrent version was committed between planning and commit: the predicted
                    // version in the parent's $ref would not match what storage would assign now.
                    throw new VersionAlreadyExistsException(groupId, schemaArtifactId, predictedVersion);
                }
            };
            Runnable rollback = () -> {
                storage.deleteArtifactVersion(groupId, schemaArtifactId, predictedVersion);
                log.info("Rolled back embedded schema version: {}/{} v{}", groupId, schemaArtifactId,
                        predictedVersion);
            };
            return new SchemaRegistration(predictedVersion, new SchemaWrite(contentWrapper, commit, rollback));
        } catch (Exception e) {
            log.warn("Failed to plan embedded schema registration {}/{}: {}", groupId, schemaArtifactId,
                    e.getMessage());
            return null;
        }
    }

    private ArtifactVersionMetaDataDto latestVersion(RegistryStorage storage, String groupId, String artifactId) {
        return storage.getArtifactVersions(groupId, artifactId, RegistryStorage.RetrievalBehavior.ALL_STATES)
                .stream().map(version -> storage.getArtifactVersionMetaData(groupId, artifactId, version))
                .max(java.util.Comparator.comparingInt(ArtifactVersionMetaDataDto::getVersionOrder))
                .orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
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

    /** The parameters shared by every field planned for one write: bundled to keep method signatures short. */
    private record PlanContext(RegistryStorage storage, String groupId, String artifactId, String owner) {
    }

    /** One field's plan: the reference it rewrites to, and how it would be registered. */
    private record FieldPlan(ArtifactReferenceDto reference, SchemaRegistration registration) {
    }

    /** @param version the version the field's $ref uses, reused or predicted
     *  @param write null if an existing version with matching content is reused (nothing to write) */
    private record SchemaRegistration(String version, SchemaWrite write) {
    }

    /** @param content the schema content, for reference resolution and rules before commit runs
     *  @param commit writes the schema; throws if its predicted coordinates were taken concurrently
     *  @param rollback undoes {@code commit} after it succeeded; best-effort, used only when a later
     *                  field in the same plan fails to commit */
    private record SchemaWrite(ContentWrapperDto content, Runnable commit, Runnable rollback) {
    }
}
