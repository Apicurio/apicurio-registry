package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.storage.error.ContentAlreadyExistsException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactReferenceDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentWithIdMapper;
import io.apicurio.registry.rest.ConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils.notEmpty;

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
     * Set the HandleFactory to use for database operations.
     * This allows storage implementations to override the default injected HandleFactory.
     */
    public void setHandleFactory(HandleFactory handleFactory) {
        this.handles = handleFactory;
    }

    @Inject
    SqlSequenceRepository sequenceRepository;

    @Inject
    RegistryStorageContentUtils utils;

    @ConfigProperty(name = "apicurio.storage.references.max-depth", defaultValue = "100")
    @Info(category = CATEGORY_STORAGE, description = "Maximum recursion depth for resolving schema references. Prevents stack overflow from deeply nested schemas.", registryAvailableSince = "3.0.6")
    int maxReferenceDepth;

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
            Set<String> visited = new HashSet<>();
            resolveReferencesRecursive(handle, result, references, 0, visited);
            return result;
        }
    }

    /**
     * Batch size for loading references. This limit ensures database compatibility (SQL Server has ~2000
     * parameter limit).
     */
    private static final int BATCH_SIZE = 100;

    /**
     * Resolves references recursively using batched database operations for improved performance. For N
     * references at depth D, this approach uses D metadata queries + D content queries instead of N^D
     * queries.
     *
     * @param handle Database handle
     * @param resolvedReferences Map to collect resolved references
     * @param references List of references to resolve
     * @param currentDepth Current recursion depth (0-based)
     * @param visited Set of already-visited reference keys for cycle detection
     */
    private void resolveReferencesRecursive(Handle handle, Map<String, TypedContent> resolvedReferences,
            List<ArtifactReferenceDto> references, int currentDepth, Set<String> visited) {
        if (references == null || references.isEmpty()) {
            return;
        }

        // Check maximum depth limit to prevent stack overflow
        if (currentDepth > maxReferenceDepth) {
            log.warn("Maximum reference resolution depth ({}) exceeded at depth {}. "
                    + "Stopping resolution. This may indicate deeply nested schemas or a configuration issue.",
                    maxReferenceDepth, currentDepth);
            return;
        }

        // Filter out already-resolved references, visited references, and invalid ones
        List<ArtifactReferenceDto> unresolvedReferences = new ArrayList<>();
        for (ArtifactReferenceDto reference : references) {
            if (reference.getArtifactId() == null || reference.getName() == null
                    || reference.getVersion() == null) {
                throw new IllegalStateException("Invalid reference: " + reference);
            }

            String referenceKey = buildReferenceKey(reference);

            // Check for cycles using the visited set
            if (visited.contains(referenceKey)) {
                log.debug("Skipping already-visited reference to prevent cycles: {}", referenceKey);
                continue;
            }

            if (!resolvedReferences.containsKey(reference.getName())) {
                unresolvedReferences.add(reference);
                visited.add(referenceKey);
            }
        }

        if (unresolvedReferences.isEmpty()) {
            return;
        }

        // Batch load all metadata
        Map<String, ArtifactVersionMetaDataDto> metadataByKey = batchLoadMetadata(handle,
                unresolvedReferences);

        // Collect all contentIds we need to load
        Set<Long> contentIds = new HashSet<>();
        for (ArtifactVersionMetaDataDto meta : metadataByKey.values()) {
            contentIds.add(meta.getContentId());
        }

        // Batch load all content
        Map<Long, ContentWrapperDto> contentById = batchLoadContent(handle, new ArrayList<>(contentIds));

        // Process results and collect nested references
        List<ArtifactReferenceDto> nestedReferences = new ArrayList<>();
        for (ArtifactReferenceDto reference : unresolvedReferences) {
            String key = buildReferenceKey(reference);
            ArtifactVersionMetaDataDto meta = metadataByKey.get(key);
            if (meta == null) {
                log.debug("Metadata not found for reference: {}", key);
                continue;
            }

            ContentWrapperDto content = contentById.get(meta.getContentId());
            if (content == null) {
                log.debug("Content not found for reference: {} with contentId: {}", key, meta.getContentId());
                continue;
            }

            // Collect nested references for next batch
            if (content.getReferences() != null) {
                for (ArtifactReferenceDto nestedRef : content.getReferences()) {
                    String nestedKey = buildReferenceKey(nestedRef);
                    if (!resolvedReferences.containsKey(nestedRef.getName())
                            && !visited.contains(nestedKey)) {
                        nestedReferences.add(nestedRef);
                    }
                }
            }
            // Add to resolved map
            TypedContent typedContent = TypedContent.create(content.getContent(),
                    content.getContentType());
            resolvedReferences.put(reference.getName(), typedContent);
        }

        // Recursively resolve nested references (batched at each level)
        if (!nestedReferences.isEmpty()) {
            resolveReferencesRecursive(handle, resolvedReferences, nestedReferences, currentDepth + 1,
                    visited);
        }
    }

    /**
     * Creates a unique key from groupId:artifactId:version for deduplication and lookup.
     */
    private String buildReferenceKey(ArtifactReferenceDto reference) {
        return normalizeGroupId(reference.getGroupId()) + ":" + reference.getArtifactId() + ":"
                + reference.getVersion();
    }

    /**
     * Batch loads metadata for a list of references in one or more queries using OR conditions.
     */
    private Map<String, ArtifactVersionMetaDataDto> batchLoadMetadata(Handle handle,
            List<ArtifactReferenceDto> references) {
        Map<String, ArtifactVersionMetaDataDto> result = new LinkedHashMap<>();
        if (references == null || references.isEmpty()) {
            return result;
        }

        // Process in batches to avoid database parameter limits
        for (int i = 0; i < references.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, references.size());
            List<ArtifactReferenceDto> batch = references.subList(i, end);
            batchLoadMetadataChunk(handle, batch, result);
        }

        return result;
    }

    /**
     * Loads metadata for a single batch chunk.
     */
    private void batchLoadMetadataChunk(Handle handle, List<ArtifactReferenceDto> batch,
            Map<String, ArtifactVersionMetaDataDto> result) {
        // Build OR conditions for each reference
        StringBuilder conditions = new StringBuilder();
        List<String> params = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            ArtifactReferenceDto ref = batch.get(i);
            if (i > 0) {
                conditions.append(" OR ");
            }
            conditions.append("(v.groupId = ? AND v.artifactId = ? AND v.version = ?)");
            params.add(normalizeGroupId(ref.getGroupId()));
            params.add(ref.getArtifactId());
            params.add(ref.getVersion());
        }

        // Build and execute query
        String sql = sqlStatements.selectArtifactVersionMetaDataBatch().replace("REFERENCES_CONDITION",
                conditions.toString());

        var query = handle.createQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            query.bind(i, params.get(i));
        }

        List<ArtifactVersionMetaDataDto> metadataList = query.map(ArtifactVersionMetaDataDtoMapper.instance)
                .list();

        // Map results by reference key
        for (ArtifactVersionMetaDataDto meta : metadataList) {
            String key = normalizeGroupId(meta.getGroupId()) + ":" + meta.getArtifactId() + ":"
                    + meta.getVersion();
            result.put(key, meta);
        }
    }

    /**
     * Batch loads content for a list of contentIds in one or more queries using IN clause.
     */
    private Map<Long, ContentWrapperDto> batchLoadContent(Handle handle, List<Long> contentIds) {
        Map<Long, ContentWrapperDto> result = new LinkedHashMap<>();
        if (contentIds == null || contentIds.isEmpty()) {
            return result;
        }

        // Process in batches to avoid database parameter limits
        for (int i = 0; i < contentIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, contentIds.size());
            List<Long> batch = contentIds.subList(i, end);
            batchLoadContentChunk(handle, batch, result);
        }

        return result;
    }

    /**
     * Loads content for a single batch chunk.
     */
    private void batchLoadContentChunk(Handle handle, List<Long> batch,
            Map<Long, ContentWrapperDto> result) {
        // Build IN clause with proper number of placeholders
        String placeholders = batch.stream().map(id -> "?").collect(Collectors.joining(", "));

        String sql = sqlStatements.selectContentByIdBatch().replace("(?)", "(" + placeholders + ")");

        var query = handle.createQuery(sql);
        for (int i = 0; i < batch.size(); i++) {
            query.bind(i, batch.get(i));
        }

        List<Map.Entry<Long, ContentWrapperDto>> contentList = query.map(ContentWithIdMapper.instance).list();

        // Map results by contentId
        for (Map.Entry<Long, ContentWrapperDto> entry : contentList) {
            result.put(entry.getKey(), entry.getValue());
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

    /**
     * Check if the registry is empty (no content stored).
     */
    public boolean isEmpty() {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllContentCount()).mapTo(Long.class).one() == 0;
        });
    }

    /**
     * Get inbound artifact references for a specific artifact version.
     */
    public List<ArtifactReferenceDto> getInboundArtifactReferences(String groupId, String artifactId,
            String version) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectInboundContentReferencesByGAV())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(ArtifactReferenceDtoMapper.instance).list();
        });
    }

    // ==================== CONTENT CREATION ====================

    /**
     * Make sure the content exists in the database (try to insert it). Regardless of whether it already
     * existed or not, return the contentId of the content in the DB.
     *
     * @param artifactType The type of artifact
     * @param contentDto The content wrapper containing the content and references
     * @param isDraft Whether this is draft content
     * @param draftProductionMode When true and isDraft is true, use real content hashes instead of draft: prefix
     */
    public Long ensureContentAndGetId(String artifactType, ContentWrapperDto contentDto, boolean isDraft,
            boolean draftProductionMode) {
        List<ArtifactReferenceDto> references = contentDto.getReferences();

        // Deduplicate references to handle cases where callers (like Avro converters)
        // may send duplicate references for nested schemas. This must be done BEFORE
        // calculating content hashes to ensure consistency.
        if (references != null && !references.isEmpty()) {
            references = references.stream()
                    .distinct()
                    .collect(Collectors.toList());
        }

        TypedContent content = TypedContent.create(contentDto.getContent(), contentDto.getContentType());
        String contentHash;
        String canonicalContentHash;
        String serializedReferences;

        // Need to create the content hash and canonical content hash. If the content is DRAFT
        // content and draftProductionMode is NOT enabled, then do NOT calculate those hashes
        // because we don't want DRAFT content to be looked up by those hashes.
        // When draftProductionMode is enabled, drafts behave like production content with real hashes.
        if (isDraft && !draftProductionMode) {
            contentHash = "draft:" + UUID.randomUUID().toString();
            canonicalContentHash = "draft:" + UUID.randomUUID().toString();
            serializedReferences = null;
        } else if (notEmpty(references)) {
            final List<ArtifactReferenceDto> finalReferences = references;
            Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver = (refs) -> {
                return handles.withHandle(handle -> {
                    return resolveReferencesRaw(handle, refs);
                });
            };
            contentHash = utils.getContentHash(content, finalReferences);
            canonicalContentHash = utils.getCanonicalContentHash(content, artifactType, finalReferences,
                    referenceResolver);
            serializedReferences = RegistryContentUtils.serializeReferences(finalReferences);
        } else {
            contentHash = utils.getContentHash(content, null);
            canonicalContentHash = utils.getCanonicalContentHash(content, artifactType, null, null);
            serializedReferences = null;
        }

        // Ensure the content is in the DB.
        final String finalContentHash = contentHash;
        final String finalCanonicalContentHash = canonicalContentHash;
        final String finalSerializedReferences = serializedReferences;
        final List<ArtifactReferenceDto> finalReferences = references;

        handles.withHandleNoException(handle -> {
            long contentId = sequenceRepository.nextContentIdRaw(handle);

            try {
                handle.createUpdate(sqlStatements.insertContent())
                        .bind(0, contentId)
                        .bind(1, finalCanonicalContentHash)
                        .bind(2, finalContentHash)
                        .bind(3, content.getContentType())
                        .bind(4, content.getContent().bytes())
                        .bind(5, finalSerializedReferences)
                        .execute();
            } catch (Exception e) {
                if (sqlStatements.isPrimaryKeyViolation(e)) {
                    log.debug("Content with content hash {} already exists: {}", finalContentHash, content);
                    return null;
                } else {
                    throw e;
                }
            }

            // If we get here, then the content was inserted and we need to insert the references.
            insertReferencesRaw(handle, contentId, finalReferences);
            return null;
        });

        // Get the contentId using the unique contentHash.
        Optional<Long> contentId = contentIdFromHash(contentHash);
        return contentId.orElseThrow(() -> new RegistryStorageException("Failed to ensure content."));
    }
}
