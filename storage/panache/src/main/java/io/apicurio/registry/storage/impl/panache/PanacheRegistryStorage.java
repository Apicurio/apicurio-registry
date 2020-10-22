package io.apicurio.registry.storage.impl.panache;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.panache.entity.Artifact;
import io.apicurio.registry.storage.impl.panache.entity.Content;
import io.apicurio.registry.storage.impl.panache.entity.Version;
import io.apicurio.registry.storage.impl.panache.repository.ArtifactRepository;
import io.apicurio.registry.storage.impl.panache.repository.ContentRepository;
import io.apicurio.registry.storage.impl.panache.repository.LabelRepository;
import io.apicurio.registry.storage.impl.panache.repository.PropertyRepository;
import io.apicurio.registry.storage.impl.panache.repository.VersionRepository;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * A Panache implementation of the {@link RegistryStorage} interface.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
public class PanacheRegistryStorage extends AbstractRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(PanacheRegistryStorage.class);

    @Inject
    ArtifactRepository artifactRepository;

    @Inject
    ContentRepository contentRepository;

    @Inject
    VersionRepository versionRepository;

    @Inject
    PropertyRepository propertyRepository;

    @Inject
    LabelRepository labelRepository;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState)
     */
    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state) {
        log.debug("Updating the state of artifact {} to {}", artifactId, state.name());

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        log.debug("Updating the state of artifact {}, version {} to {}", artifactId, version, state.name());
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType,
                                                               ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact row for: {}", artifactId);
        String createdBy = null;
        Date createdOn = new Date();

        try {
            // Extract meta-data from the content
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentExtractor extractor = provider.getContentExtractor();
            EditableMetaData emd = extractor.extract(content);
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(emd.getName(), emd.getDescription(), emd.getLabels(), emd.getProperties());

            ArtifactMetaDataDto amdd = createArtifactInternal(artifactId, artifactType, content,
                    createdBy, createdOn, metaData);

            return CompletableFuture.completedFuture(amdd);

        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * Creates an artifact version by storing content in the versions table.
     *
     * @param firstVersion
     * @param artifact
     * @param contentHandle
     */
    private ArtifactVersionMetaDataDto createArtifactVersion(ArtifactType artifactType,
                                                             boolean firstVersion, Artifact artifact, String name, String description, List<String> labels,
                                                             Map<String, String> properties, ContentHandle contentHandle) {

        final ArtifactState state = ArtifactState.ENABLED;
        String createdBy = null;
        final Date createdOn = new Date();
        final byte[] contentBytes = contentHandle.bytes();
        final String contentHash = DigestUtils.sha256Hex(contentBytes);
        final String labelsStr = SqlUtil.serializeLabels(labels);
        final String propertiesStr = SqlUtil.serializeProperties(properties);

        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
        ContentHandle canonicalContent = canonicalizer.canonicalize(contentHandle);
        byte[] canonicalContentBytes = canonicalContent.bytes();
        String canonicalContentHash = DigestUtils.sha256Hex(canonicalContentBytes);

        final Content content = contentRepository.upsertContentByHash(contentHash, contentBytes, canonicalContentHash);
        final Version version = versionRepository.createVersion(firstVersion, artifact, name, description, state, createdBy, createdOn, labelsStr, propertiesStr, content);

        labelRepository.persistLabels(labels, version);
        propertyRepository.persistProperties(properties, version);

        //TODO extract
        final ArtifactVersionMetaDataDto artifactVersionMetaDataDto = new ArtifactVersionMetaDataDto();
        artifactVersionMetaDataDto.setVersion(version.version.intValue());
        artifactVersionMetaDataDto.setCreatedBy(version.createdBy);
        artifactVersionMetaDataDto.setCreatedOn(version.createdOn.toInstant().toEpochMilli());
        artifactVersionMetaDataDto.setDescription(version.description);
        artifactVersionMetaDataDto.setGlobalId(version.globalId);
        artifactVersionMetaDataDto.setName(version.name);
        artifactVersionMetaDataDto.setState(ArtifactState.fromValue(version.state));
        artifactVersionMetaDataDto.setType(artifactType);

        return artifactVersionMetaDataDto;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
                                                                           ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        log.debug("Inserting an artifact (with meta-data) row for: {}", artifactId);
        String createdBy = null;
        Date createdOn = new Date();
        ArtifactMetaDataDto amdd = createArtifactInternal(artifactId, artifactType, content,
                createdBy, createdOn, metaData);

        return CompletableFuture.completedFuture(amdd);
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifact(java.lang.String)
     */
    @Override
    @Transactional
    public SortedSet<Long> deleteArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {}", artifactId);
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    @Transactional
    public StoredArtifact getArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {

        log.debug("Selecting a single artifact (latest version) by artifactId: {}", artifactId);

        final Version version = versionRepository.getArtifactLatestVersion(artifactId);

        return StoredArtifact.builder()
                .content(ContentHandle.create(version.content.content))
                .globalId(version.globalId)
                .version(version.version)
                .build();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType,
                                                               ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating artifact {} with a new version (content).", artifactId);
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId,
                                                                           ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactIds(java.lang.Integer)
     */
    @Override
    @Transactional
    public Set<String> getArtifactIds(Integer limit) {
        log.debug("Getting the set of all artifact IDs");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchArtifacts(java.lang.String, int, int, io.apicurio.registry.rest.beans.SearchOver, io.apicurio.registry.rest.beans.SortOrder)
     */
    @Override
    @Transactional
    public ArtifactSearchResults searchArtifacts(String search, int offset, int limit, SearchOver searchOver,
                                                 SortOrder sortOrder) {
        log.debug("Searching for artifacts: {} over {} with {} ordering", search, searchOver, sortOrder);

        try {
            return versionRepository.searchArtifacts(search, offset, limit, searchOver, sortOrder);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact (latest version) meta-data: {}", artifactId);

        return this.getLatestArtifactMetaDataInternal(artifactId);
    }

    /**
     * Internal method to retrieve the meta-data of the latest version of the given artifact.
     *
     * @param artifactId
     */
    private ArtifactMetaDataDto getLatestArtifactMetaDataInternal(String artifactId) {

        final ArtifactMetaData metaData = versionRepository.getArtifactMetadata(artifactId);
        final ArtifactMetaDataDto dto = new ArtifactMetaDataDto();

        dto.setDescription(metaData.getDescription());
        dto.setGlobalId(metaData.getGlobalId());
        dto.setId(metaData.getId());
        dto.setLabels(metaData.getLabels());
        dto.setModifiedBy(metaData.getModifiedBy());
        dto.setModifiedOn(metaData.getModifiedOn());
        dto.setName(metaData.getName());
        dto.setType(metaData.getType());
        dto.setState(metaData.getState());
        dto.setProperties(metaData.getProperties());
        dto.setVersion(metaData.getVersion());

        return dto;
    }

    /**
     * Internal method to retrieve the meta-data of the given version of the given artifact.
     *
     * @param artifactId
     * @param version
     */
    private ArtifactVersionMetaDataDto getArtifactVersionMetaDataInternal(String artifactId, Long version) {

        final Version metaData = versionRepository.getVersion(artifactId, version);
        final ArtifactVersionMetaDataDto dto = new ArtifactVersionMetaDataDto();

        dto.setDescription(metaData.description);
        dto.setGlobalId(metaData.globalId);
        dto.setName(metaData.name);
        dto.setState(ArtifactState.fromValue(metaData.state));
        dto.setVersion(metaData.version.intValue());

        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("TBD - Please implement me!");
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(long)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting meta-data for globalId: {}", globalId);
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    @Transactional
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    @Transactional
    public List<RuleType> getArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRuleAsync(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule,
                                                         RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRules(java.lang.String)
     */
    @Override
    @Transactional
    public void deleteArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override
    @Transactional
    public SortedSet<Long> getArtifactVersions(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for an artifact");
        return new TreeSet<>(versionRepository.getArtifactVersions(artifactId));

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#searchVersions(java.lang.String, int, int)
     */
    @Override
    @Transactional
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(long)
     */
    @Override
    @Transactional
    public StoredArtifact getArtifactVersion(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);

        final Version version = versionRepository.findById(globalId);

        return StoredArtifact.builder()
                .content(ContentHandle.create(version.content.content))
                .globalId(globalId)
                .version(version.version)
                .build();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override
    @Transactional
    public StoredArtifact getArtifactVersion(String artifactId, long numVersion)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} and version {}", artifactId, numVersion);

        final Version version = versionRepository.getVersion(artifactId, numVersion);

        return StoredArtifact.builder()
                .content(ContentHandle.create(version.content.content))
                .globalId(version.globalId)
                .version(version.version)
                .build();
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersion(java.lang.String, long)
     */
    @Override
    @Transactional
    public void deleteArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact version meta-data: {} version {}", artifactId, version);
        try {
            return this.getArtifactVersionMetaDataInternal(artifactId, version);
        } catch (IllegalStateException e) {
            throw new VersionNotFoundException(artifactId, version);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    @Transactional
    public void updateArtifactVersionMetaData(String artifactId, long version,
                                              EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    @Transactional
    public void deleteArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRules()
     */
    @Override
    @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        return Collections.EMPTY_LIST;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRules()
     */
    @Override
    @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public RuleConfigurationDto getGlobalRule(RuleType rule)
            throws RuleNotFoundException, RegistryStorageException {
        return null;
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {

    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {

    }

    /**
     * Internal method to create a new artifact.
     *
     * @param artifactId
     * @param artifactType
     * @param content
     * @param createdBy
     * @param createdOn
     * @param metaData
     */
    private ArtifactMetaDataDto createArtifactInternal(String artifactId, ArtifactType artifactType,
                                                       ContentHandle content, String createdBy, Date createdOn, EditableArtifactMetaDataDto metaData) {

        final Artifact artifact = new Artifact(artifactId, artifactType.name(), null, Timestamp.from(Instant.now()), null);

        artifactRepository.persist(artifact);

        // Then create a row in the content and versions tables (for the content and version meta-data)
        ArtifactVersionMetaDataDto vmdd = this.createArtifactVersion(artifactType, true, artifact,
                metaData.getName(), metaData.getDescription(), metaData.getLabels(), metaData.getProperties(), content);

        // Update the "latest" column in the artifacts table with the globalId of the new version
        artifactRepository.updateLatestVersion(artifactId, vmdd.getGlobalId());

        // Return the new artifact meta-data
        ArtifactMetaDataDto amdd = versionToArtifactDto(artifactId, vmdd);
        amdd.setCreatedBy(createdBy);
        amdd.setCreatedOn(createdOn.getTime());
        amdd.setLabels(metaData.getLabels());
        amdd.setProperties(metaData.getProperties());
        return amdd;
    }

    /**
     * Converts a version DTO to an artifact DTO.
     *
     * @param artifactId
     * @param vmdd
     */
    private ArtifactMetaDataDto versionToArtifactDto(String artifactId, ArtifactVersionMetaDataDto vmdd) {
        ArtifactMetaDataDto amdd = new ArtifactMetaDataDto();
        amdd.setGlobalId(vmdd.getGlobalId());
        amdd.setId(artifactId);
        amdd.setModifiedBy(vmdd.getCreatedBy());
        amdd.setModifiedOn(vmdd.getCreatedOn());
        amdd.setState(vmdd.getState());
        amdd.setName(vmdd.getName());
        amdd.setDescription(vmdd.getDescription());
        amdd.setType(vmdd.getType());
        amdd.setVersion(vmdd.getVersion());
        return amdd;
    }
}
