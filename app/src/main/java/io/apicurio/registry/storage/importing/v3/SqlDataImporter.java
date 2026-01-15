package io.apicurio.registry.storage.importing.v3;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentHashType;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class used when importing the contents of a v3 formatted .zip file into Apicurio Registry.
 */
public class SqlDataImporter extends AbstractDataImporter {

    protected RegistryStorageContentUtils utils;

    protected final RegistryStorage storage;

    protected final boolean preserveGlobalId;

    protected final boolean preserveContentId;

    // ID remapping
    protected final Map<Long, Long> globalIdMapping = new HashMap<>();
    protected final Map<Long, Long> contentIdMapping = new HashMap<>();

    public SqlDataImporter(Logger logger, RegistryStorageContentUtils utils, RegistryStorage storage,
            boolean preserveGlobalId, boolean preserveContentId) {
        super(logger);
        this.utils = utils;
        this.storage = storage;
        this.preserveGlobalId = preserveGlobalId;
        this.preserveContentId = preserveContentId;
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        try {
            storage.importArtifactRule(entity);
            log.debug("Artifact rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import artifact rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    protected void importArtifact(ArtifactEntity entity) {
        try {
            storage.importArtifact(entity);
            log.debug("Artifact imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import artifact {} / {}: {}", entity.groupId, entity.artifactId,
                    ex.getMessage());
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

            storage.importArtifactVersion(entity);
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
            log.warn("Failed to import artifact version {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importContent(ContentEntity entity) {
        try {
            List<ArtifactReferenceDto> references = RegistryContentUtils
                    .deserializeReferences(entity.serializedReferences);

            if (entity.contentType == null) {
                throw new RuntimeException("ContentEntity is missing required field: contentType");
            }

            TypedContent typedContent = TypedContent.create(ContentHandle.create(entity.contentBytes),
                    entity.contentType);

            // Convert old hash format to new format if needed
            if (entity.isOldHashFormat()) {
                log.debug("Importing content entity with old hash format, converting to new format");
                entity.hashes = new HashMap<>();

                // Populate hashes map from old fields
                if (entity.contentHash != null) {
                    entity.hashes.put(ContentHashType.CONTENT_SHA256.value(), entity.contentHash);
                }
                if (entity.canonicalHash != null) {
                    entity.hashes.put(ContentHashType.CANONICAL_SHA256.value(), entity.canonicalHash);
                }

                // Calculate canonical-no-refs hash if we have artifactType
                if (entity.artifactType != null) {
                    try {
                        TypedContent canonicalContent = utils.canonicalizeContent(entity.artifactType, typedContent,
                                RegistryContentUtils.recursivelyResolveReferences(references,
                                        storage::getContentByReference));
                        entity.hashes.put("canonical-no-refs-sha256",
                                DigestUtils.sha256Hex(canonicalContent.getContent().bytes()));
                    } catch (Exception e) {
                        log.warn("Failed to calculate canonical-no-refs hash for old format content: {}", e.getMessage());
                    }
                }
            } else if (entity.isNewHashFormat()) {
                log.debug("Importing content entity with new hash format");
                // Ensure deprecated fields are populated for any code that might still reference them
                entity.contentHash = entity.hashes.get(ContentHashType.CONTENT_SHA256.value());
                entity.canonicalHash = entity.hashes.get(ContentHashType.CANONICAL_SHA256.value());
            } else {
                // No hashes present at all - generate them if possible
                log.warn("Content entity has no hash information, will generate if possible");
                entity.hashes = new HashMap<>();

                // Generate content hash
                String contentHash = DigestUtils.sha256Hex(entity.contentBytes);
                entity.hashes.put(ContentHashType.CONTENT_SHA256.value(), contentHash);
                entity.contentHash = contentHash;

                // Generate canonical hash if we have artifactType
                if (entity.artifactType != null) {
                    TypedContent canonicalContent = utils.canonicalizeContent(entity.artifactType, typedContent,
                            RegistryContentUtils.recursivelyResolveReferences(references,
                                    storage::getContentByReference));
                    String canonicalHash = DigestUtils.sha256Hex(canonicalContent.getContent().bytes());
                    entity.hashes.put(ContentHashType.CANONICAL_SHA256.value(), canonicalHash);
                    entity.hashes.put(ContentHashType.CANONICAL_NO_REFS_SHA256.value(), canonicalHash);
                    entity.canonicalHash = canonicalHash;
                }
            }

            var oldContentId = entity.contentId;
            if (!preserveContentId) {
                entity.contentId = storage.nextContentId();
            }

            storage.importContent(entity);
            log.debug("Content imported successfully: {}", entity);

            contentIdMapping.put(oldContentId, entity.contentId);
        } catch (Exception ex) {
            log.warn("Failed to import content {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        try {
            storage.importGlobalRule(entity);
            log.debug("Global rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import global rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGroup(GroupEntity entity) {
        try {
            storage.importGroup(entity);
            log.debug("Group imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import group {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importGroupRule(GroupRuleEntity entity) {
        try {
            storage.importGroupRule(entity);
            log.debug("Group rule imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import group rule {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    public void importComment(CommentEntity entity) {
        try {
            entity.globalId = globalIdMapping.get(entity.globalId);

            storage.importComment(entity);
            log.debug("Comment imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import comment {}: {}", entity, ex.getMessage());
        }
    }

    @Override
    protected void importBranch(BranchEntity entity) {
        try {
            storage.importBranch(entity);
            log.debug("Branch imported successfully: {}", entity);
        } catch (Exception ex) {
            log.warn("Failed to import branch {}: {}", entity, ex.getMessage());
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

        } catch (IOException ex) {
            throw new RegistryException("Could not read next entity to import", ex);
        }
    }
}
