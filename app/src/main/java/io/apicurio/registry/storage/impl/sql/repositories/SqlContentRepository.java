package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.error.ContentAlreadyExistsException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.rest.ConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Repository handling content operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlContentRepository {

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Get content by contentId.
     */
    public ContentWrapperDto getContentById(long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        return handles.<ContentWrapperDto, RuntimeException>withHandleNoException(
                handle -> getContentByIdRaw(handle, contentId));
    }

    /**
     * Get content by contentId using an existing handle.
     */
    public ContentWrapperDto getContentByIdRaw(Handle handle, long contentId)
            throws ContentNotFoundException, RegistryStorageException {
        Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements.selectContentById())
                .bind(0, contentId).map(ContentMapper.instance).findFirst();
        return res.orElseThrow(() -> new ContentNotFoundException(contentId));
    }

    /**
     * Get content by content hash.
     */
    public ContentWrapperDto getContentByHash(String contentHash)
            throws ContentNotFoundException, RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Optional<ContentWrapperDto> res = handle.createQuery(sqlStatements.selectContentByContentHash())
                    .bind(0, contentHash).map(ContentMapper.instance).findFirst();
            return res.orElseThrow(() -> new ContentNotFoundException(contentHash));
        });
    }

    /**
     * Get content by reference.
     */
    public ContentWrapperDto getContentByReference(ArtifactReferenceDto reference) {
        try {
            return handles.withHandle(handle -> {
                Optional<ArtifactVersionMetaDataDto> metaRes = handle
                        .createQuery(sqlStatements.selectArtifactVersionMetaData())
                        .bind(0, normalizeGroupId(reference.getGroupId()))
                        .bind(1, reference.getArtifactId())
                        .bind(2, reference.getVersion())
                        .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();

                if (metaRes.isEmpty()) {
                    return null;
                }

                ArtifactVersionMetaDataDto meta = metaRes.get();
                ContentWrapperDto content = getContentByIdRaw(handle, meta.getContentId());
                content.setArtifactType(meta.getArtifactType());
                return content;
            });
        } catch (VersionNotFoundException e) {
            return null;
        }
    }

    /**
     * Check if content exists by hash.
     */
    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectContentCountByHash()).bind(0, contentHash)
                    .mapTo(Integer.class).one() > 0;
        });
    }

    /**
     * Check if content exists by contentId.
     */
    public boolean isContentExistsRaw(Handle handle, long contentId) {
        return handle.createQuery(sqlStatements.selectContentExists()).bind(0, contentId)
                .mapTo(Integer.class).one() > 0;
    }

    /**
     * Get contentId from hash.
     */
    public Optional<Long> contentIdFromHash(String contentHash) {
        return handles.<Optional<Long>, RuntimeException>withHandleNoException(
                handle -> contentIdFromHashRaw(handle, contentHash));
    }

    /**
     * Get contentId from hash using an existing handle.
     */
    public Optional<Long> contentIdFromHashRaw(Handle handle, String contentHash) {
        return handle.createQuery(sqlStatements.selectContentIdByHash()).bind(0, contentHash)
                .mapTo(Long.class).findOne();
    }

    /**
     * Insert content into the database.
     */
    public void ensureContent(Handle handle, long contentId, TypedContent content, String contentHash,
            String canonicalContentHash, List<ArtifactReferenceDto> references, String referencesSerialized) {
        try {
            handle.createUpdate(sqlStatements.insertContent())
                    .bind(0, contentId)
                    .bind(1, canonicalContentHash)
                    .bind(2, contentHash)
                    .bind(3, content.getContentType())
                    .bind(4, content.getContent().bytes())
                    .bind(5, referencesSerialized)
                    .execute();
        } catch (Exception e) {
            if (sqlStatements.isPrimaryKeyViolation(e)) {
                log.debug("Content with content hash {} already exists: {}", contentHash, content);
                return;
            } else {
                throw e;
            }
        }

        // Insert references
        insertReferencesRaw(handle, contentId, references);
    }

    /**
     * Insert content references.
     */
    public void insertReferencesRaw(Handle handle, Long contentId, List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            references.forEach(reference -> {
                try {
                    handle.createUpdate(sqlStatements.insertContentReference())
                            .bind(0, contentId)
                            .bind(1, normalizeGroupId(reference.getGroupId()))
                            .bind(2, reference.getArtifactId())
                            .bind(3, reference.getVersion())
                            .bind(4, reference.getName())
                            .execute();
                } catch (Exception e) {
                    if (sqlStatements.isPrimaryKeyViolation(e)) {
                        throw new ConflictException("Duplicate reference found: " + reference);
                    } else {
                        throw e;
                    }
                }
            });
        }
    }

    /**
     * Update content canonical hash.
     */
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        handles.withHandleNoException(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateContentCanonicalHash())
                    .bind(0, newCanonicalHash).bind(1, contentId).bind(2, contentHash).execute();
            if (rowCount == 0) {
                log.warn("update content canonicalHash, no row match contentId {} contentHash {}", contentId,
                        contentHash);
            }
            return null;
        });
    }

    /**
     * Get enabled artifact content IDs.
     */
    public List<Long> getEnabledArtifactContentIds(String groupId, String artifactId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectArtifactContentIds())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                    .mapTo(Long.class).list();
        });
    }

    /**
     * Get content IDs referencing an artifact version.
     */
    public List<Long> getContentIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectContentIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version).mapTo(Long.class)
                    .list();
        });
    }

    /**
     * Get global IDs referencing an artifact version.
     */
    public List<Long> getGlobalIdsReferencingArtifactVersion(String groupId, String artifactId,
            String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalIdsReferencingArtifactVersionBy())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version).mapTo(Long.class)
                    .list();
        });
    }

    /**
     * Get global IDs referencing an artifact.
     */
    public List<Long> getGlobalIdsReferencingArtifact(String groupId, String artifactId) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectGlobalIdsReferencingArtifactBy())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Long.class)
                    .list();
        });
    }

    /**
     * Delete all orphaned content.
     */
    public void deleteAllOrphanedContentRaw(Handle handle) {
        log.debug("Deleting all orphaned content");
        handle.createUpdate(sqlStatements.deleteOrphanedContentReferences()).execute();
        handle.createUpdate(sqlStatements.deleteAllOrphanedContent()).execute();
    }

    /**
     * Resolve references to their content.
     */
    public Map<String, TypedContent> resolveReferencesRaw(Handle handle,
            List<ArtifactReferenceDto> references) {
        if (references == null || references.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Map<String, TypedContent> result = new LinkedHashMap<>();
            resolveReferencesRecursive(handle, result, references);
            return result;
        }
    }

    private void resolveReferencesRecursive(Handle handle, Map<String, TypedContent> resolvedReferences,
            List<ArtifactReferenceDto> references) {
        if (references != null && !references.isEmpty()) {
            for (ArtifactReferenceDto reference : references) {
                if (reference.getArtifactId() == null || reference.getName() == null
                        || reference.getVersion() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                } else {
                    if (!resolvedReferences.containsKey(reference.getName())) {
                        try {
                            Optional<ArtifactVersionMetaDataDto> metaRes = handle
                                    .createQuery(sqlStatements.selectArtifactVersionMetaData())
                                    .bind(0, normalizeGroupId(reference.getGroupId()))
                                    .bind(1, reference.getArtifactId())
                                    .bind(2, reference.getVersion())
                                    .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();

                            if (metaRes.isPresent()) {
                                ArtifactVersionMetaDataDto meta = metaRes.get();
                                ContentWrapperDto referencedContent = getContentByIdRaw(handle,
                                        meta.getContentId());
                                resolveReferencesRecursive(handle, resolvedReferences,
                                        referencedContent.getReferences());
                                TypedContent typedContent = TypedContent.create(referencedContent.getContent(),
                                        referencedContent.getContentType());
                                resolvedReferences.put(reference.getName(), typedContent);
                            }
                        } catch (VersionNotFoundException ex) {
                            // Ignored
                        }
                    }
                }
            }
        }
    }

    // ==================== IMPORT OPERATIONS ====================

    /**
     * Import a content entity (used for data import/migration).
     */
    public void importContent(ContentEntity entity) {
        handles.withHandleNoException(handle -> {
            if (!isContentExistsRaw(handle, entity.contentId)) {
                handle.createUpdate(sqlStatements.importContent())
                        .bind(0, entity.contentId)
                        .bind(1, entity.canonicalHash)
                        .bind(2, entity.contentHash)
                        .bind(3, entity.contentType)
                        .bind(4, entity.contentBytes)
                        .bind(5, entity.serializedReferences)
                        .execute();

                insertReferencesRaw(handle, entity.contentId,
                        RegistryContentUtils.deserializeReferences(entity.serializedReferences));
            } else {
                throw new ContentAlreadyExistsException(entity.contentId);
            }
            return null;
        });
    }
}
