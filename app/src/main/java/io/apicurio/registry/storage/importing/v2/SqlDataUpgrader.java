package io.apicurio.registry.storage.importing.v2;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.v2.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v2.CommentEntity;
import io.apicurio.registry.utils.impexp.v2.ContentEntity;
import io.apicurio.registry.utils.impexp.v2.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v2.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.types.ArtifactType.ASYNCAPI;
import static io.apicurio.registry.types.ArtifactType.AVRO;
import static io.apicurio.registry.types.ArtifactType.GRAPHQL;
import static io.apicurio.registry.types.ArtifactType.JSON;
import static io.apicurio.registry.types.ArtifactType.OPENAPI;
import static io.apicurio.registry.types.ArtifactType.PROTOBUF;
import static io.apicurio.registry.types.ArtifactType.WSDL;
import static io.apicurio.registry.types.ArtifactType.XML;
import static io.apicurio.registry.types.ArtifactType.XSD;

/**
 * This class takes a stream of Registry v2 entities and imports them into the application using
 * {@link SqlDataUpgrader#importData(EntityInputStream, Runnable)} as it's entry point. It must be used in the
 * upgrade process from v2 to v3.
 */
public class SqlDataUpgrader extends AbstractDataImporter {

    protected RegistryStorageContentUtils utils;

    protected final RegistryStorage storage;

    protected final boolean preserveGlobalId;
    protected final boolean preserveContentId;

    // ID remapping
    protected final Map<Long, Long> globalIdMapping = new HashMap<>();
    protected final Map<Long, Long> contentIdMapping = new HashMap<>();

    // We may need to recalculate the canonical hash for some content after the
    // import is complete.
    private Set<Long> deferredCanonicalHashContentIds = new HashSet<>();

    public SqlDataUpgrader(Logger logger, RegistryStorageContentUtils utils, RegistryStorage storage,
            boolean preserveGlobalId, boolean preserveContentId) {
        super(logger);
        this.utils = utils;
        this.storage = storage;
        this.preserveGlobalId = preserveGlobalId;
        this.preserveContentId = preserveContentId;
    }

    @Override
    protected void importArtifactRule(ArtifactRuleEntity entity) {
        try {
            io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity newEntity = io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity
                    .builder().type(entity.type).artifactId(entity.artifactId)
                    .configuration(entity.configuration).groupId(entity.groupId).build();
            storage.importArtifactRule(newEntity);
            log.debug("Artifact rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import artifact rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        try {
            entity.contentId = contentIdMapping.get(entity.contentId);

            var oldGlobalId = entity.globalId;
            if (!preserveGlobalId) {
                entity.globalId = storage.nextGlobalId();
            }

            Map<String, String> artifactVersionLabels = new HashMap<>();

            if (entity.labels != null) {
                entity.labels.forEach(label -> artifactVersionLabels.put(label, null));
            }

            if (entity.properties != null) {
                artifactVersionLabels.putAll(entity.properties);
            }

            io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity newEntity = io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity
                    .builder().createdOn(entity.createdOn).description(entity.description)
                    .labels(artifactVersionLabels).name(entity.name).owner(entity.createdBy)
                    .state(VersionState.fromValue(entity.state.value())).artifactId(entity.artifactId)
                    .versionOrder(entity.versionId).modifiedOn(entity.createdOn).modifiedBy(entity.createdBy)
                    .version(entity.version).globalId(entity.globalId).contentId(entity.contentId)
                    .groupId(entity.groupId).build();

            // If the version being imported is the first one, we have to create the artifact first
            if (!storage.isArtifactExists(entity.groupId, entity.artifactId)) {
                ArtifactEntity artifactEntity = ArtifactEntity.builder().artifactId(entity.artifactId)
                        .artifactType(entity.artifactType).createdOn(entity.createdOn)
                        .description(entity.description).groupId(entity.groupId).labels(artifactVersionLabels)
                        .modifiedBy(entity.createdBy).modifiedOn(entity.createdOn).name(entity.name)
                        .owner(entity.createdBy).build();
                storage.importArtifact(artifactEntity);
            }

            if (entity.isLatest) {
                // If this version is the latest, update the artifact metadata with its metadata
                EditableArtifactMetaDataDto editableArtifactMetaDataDto = EditableArtifactMetaDataDto
                        .builder().name(newEntity.name).owner(newEntity.owner)
                        .description(newEntity.description).labels(newEntity.labels).build();

                storage.updateArtifactMetaData(newEntity.groupId, newEntity.artifactId,
                        editableArtifactMetaDataDto);
            }

            storage.importArtifactVersion(newEntity);
            log.debug("Artifact version imported successfully: {}", entity);
            globalIdMapping.put(oldGlobalId, entity.globalId);
        } catch (VersionAlreadyExistsException ex) {
            if (ex.getGlobalId() != null) {
                log.warn("Duplicate globalId {} detected, skipping import of artifact version: {}",
                        ex.getGlobalId(), entity);
            } else {
                log.warn("Failed to import artifact version {}: {}", entity, ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Failed to import artifact version {}: {}", entity, ex.getMessage(), ex);
        }
    }

    @Override
    public void importContent(ContentEntity entity) {
        try {
            // Based on the configuration, a new id is requested or the old one is used.
            var oldContentId = entity.contentId;
            if (!preserveContentId) {
                entity.contentId = storage.nextContentId();
            }

            List<ArtifactReferenceDto> references = SqlUtil
                    .deserializeReferences(entity.serializedReferences);

            // Recalculate the hash using the current algorithm
            TypedContent typedContent = TypedContent.create(ContentHandle.create(entity.contentBytes), null);
            entity.contentHash = utils.getContentHash(typedContent, references);

            // Try to recalculate the canonical hash - this may fail if the content has references
            try {
                Map<String, TypedContent> resolvedReferences = storage.resolveReferences(references);
                entity.artifactType = utils.determineArtifactType(typedContent, null, resolvedReferences);

                // First we have to recalculate both the canonical hash and the contentHash
                TypedContent canonicalContent = utils.canonicalizeContent(entity.artifactType, typedContent,
                        resolvedReferences);

                entity.canonicalHash = DigestUtils.sha256Hex(canonicalContent.getContent().bytes());
            } catch (Exception ex) {
                log.debug("Deferring canonical hash calculation: " + ex.getMessage());
                deferredCanonicalHashContentIds.add(entity.contentId);
                // Default to AVRO in the case of failure to determine a type (same default that v2 would have
                // used).
                // Note: the artifactType is not saved to the DB, but is used to determine the content-type.
                // So a
                // default of AVRO will result in a (sensible) default of application/json for the
                // content-type.
                // This works for the v2 -> v3 upgrader because only JSON based types will potentially fail
                // when
                // trying to canonicalize content without fully resolved references.
                //
                // If this assumption is wrong (e.g. for PROTOBUF) then we'll need an extra step here to
                // figure
                // out if the core content is JSON or PROTO.
                entity.artifactType = AVRO;
            }

            // Finally, using the information from the old content, a V3 content entity is created.
            io.apicurio.registry.utils.impexp.v3.ContentEntity newEntity = io.apicurio.registry.utils.impexp.v3.ContentEntity
                    .builder().contentType(determineContentType(entity.artifactType, typedContent))
                    .contentHash(entity.contentHash).artifactType(entity.artifactType)
                    .contentBytes(entity.contentBytes).serializedReferences(entity.serializedReferences)
                    .canonicalHash(entity.canonicalHash).contentId(entity.contentId).build();

            storage.importContent(newEntity);
            log.debug("Content imported successfully: {}", entity);

            contentIdMapping.put(oldContentId, entity.contentId);
        } catch (Exception ex) {
            log.warn("Failed to import content {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        try {
            storage.importGlobalRule(io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity.builder()
                    .configuration(entity.configuration).ruleType(entity.ruleType).build());
            log.debug("Global rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import global rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGroup(GroupEntity entity) {
        try {
            io.apicurio.registry.utils.impexp.v3.GroupEntity newEntity = io.apicurio.registry.utils.impexp.v3.GroupEntity
                    .builder().artifactsType(entity.artifactsType).createdOn(entity.createdOn)
                    .description(entity.description).groupId(entity.groupId).labels(Collections.emptyMap())
                    .modifiedBy(entity.modifiedBy).modifiedOn(entity.modifiedOn).owner(entity.createdBy)
                    .build();
            storage.importGroup(newEntity);
            log.debug("Group imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import group {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importComment(CommentEntity entity) {
        try {
            entity.globalId = globalIdMapping.get(entity.globalId);

            io.apicurio.registry.utils.impexp.v3.CommentEntity newEntity = io.apicurio.registry.utils.impexp.v3.CommentEntity
                    .builder().commentId(entity.commentId).createdOn(entity.createdOn)
                    .globalId(entity.globalId).owner(entity.createdBy).value(entity.value).build();

            storage.importComment(newEntity);
            log.debug("Comment imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import comment {}: {}", entity, ex.getMessage());
        }
    }

    /**
     * WARNING: Must be executed within a transaction!
     */
    @Override
    public void importData(EntityInputStream entities, Runnable postImportAction) {
        try {
            Entity entity = null;
            while ((entity = entities.nextEntity()) != null) {
                importEntity(entity);
            }

            postImportAction.run();

            // Make sure the contentId sequence is set high enough
            storage.resetContentId();

            // Make sure the globalId sequence is set high enough
            storage.resetGlobalId();

            // Make sure the commentId sequence is set high enough
            storage.resetCommentId();

            // Recalculate any deferred content IDs
            deferredCanonicalHashContentIds.forEach(id -> {
                recalculateCanonicalHash(id);
            });

        } catch (IOException ex) {
            throw new RegistryException("Could not read next entity to import", ex);
        }
    }

    private void recalculateCanonicalHash(Long contentId) {
        try {
            ContentWrapperDto wrapperDto = storage.getContentById(contentId);
            List<ArtifactReferenceDto> references = wrapperDto.getReferences();

            List<ArtifactVersionMetaDataDto> versions = storage.getArtifactVersionsByContentId(contentId);
            if (versions.isEmpty()) {
                // Orphaned content - who cares?
                return;
            }

            TypedContent content = TypedContent.create(wrapperDto.getContent(), wrapperDto.getContentType());
            String artifactType = versions.get(0).getArtifactType();
            Map<String, TypedContent> resolvedReferences = storage.resolveReferences(references);
            TypedContent canonicalContent = utils.canonicalizeContent(artifactType, content,
                    resolvedReferences);
            String canonicalHash = DigestUtils.sha256Hex(canonicalContent.getContent().bytes());
            String contentHash = utils.getContentHash(content, references);

            storage.updateContentCanonicalHash(canonicalHash, contentId, contentHash);
        } catch (Exception ex) {
            // Oh well, we did our best.
            log.warn("Failed to recalculate canonical hash for: " + contentId, ex);
        }
    }

    private String determineContentType(String artifactTypeHint, TypedContent content) {
        if (content.getContentType() != null) {
            return content.getContentType();
        } else {
            switch (artifactTypeHint) {
                case ASYNCAPI:
                case JSON:
                case OPENAPI:
                case AVRO:
                    // WARNING: This is only safe here. We can safely return JSON because in V2 we were
                    // transforming all YAML to JSON before storing the content in the database.
                    return ContentTypes.APPLICATION_JSON;
                case PROTOBUF:
                    return ContentTypes.APPLICATION_PROTOBUF;
                case GRAPHQL:
                    return ContentTypes.APPLICATION_GRAPHQL;
                case XML:
                case XSD:
                case WSDL:
                    return ContentTypes.APPLICATION_XML;
            }
        }
        throw new InvalidArtifactTypeException("Invalid or unknown artifact type: " + artifactTypeHint);
    }
}
