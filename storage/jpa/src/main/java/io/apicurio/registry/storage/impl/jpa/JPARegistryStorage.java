/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.jpa;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.exception.ArtifactAlreadyExistsException;
import io.apicurio.registry.exception.ArtifactNotFoundException;
import io.apicurio.registry.exception.RegistryStorageException;
import io.apicurio.registry.exception.RuleAlreadyExistsException;
import io.apicurio.registry.exception.RuleNotFoundException;
import io.apicurio.registry.exception.VersionNotFoundException;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.storage.*;
import io.apicurio.registry.storage.impl.AbstractRegistryStorage;
import io.apicurio.registry.storage.impl.jpa.entity.Artifact;
import io.apicurio.registry.storage.impl.jpa.entity.MetaData;
import io.apicurio.registry.storage.impl.jpa.entity.Rule;
import io.apicurio.registry.storage.impl.jpa.entity.RuleConfig;
import io.apicurio.registry.storage.impl.jpa.search.ArtifactSearchResult;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.DtoUtil;
import io.apicurio.registry.util.SearchUtil;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.*;
import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static io.apicurio.registry.utils.StringUtil.isEmpty;
import static java.util.Objects.requireNonNull;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT, description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT})
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT, description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT})
@Timed(name = STORAGE_OPERATION_TIME, description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS)
@Logged
public class JPARegistryStorage extends AbstractRegistryStorage {

    private static final int ARTIFACT_FIRST_VERSION = 1;

    @Inject
    EntityManager entityManager;

    @Inject
    JPAEntityMapper mapper;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private long _getNextArtifactVersion(String artifactId) {

        requireNonNull(artifactId);
        Long latest = null;

        try {

            latest = entityManager.createQuery("SELECT a.version from Artifact a where a.artifactId || '===' || a.version ="
                    + " (SELECT a2.artifactId || '===' || MAX(a2.version) FROM Artifact a2 "
                    +  "WHERE a2.artifactId = :artifact_id "
                    + "GROUP BY a2.artifactId)", Long.class)
                    .setParameter("artifact_id", artifactId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getSingleResult();

        } catch (NoResultException ex) {
        }

        return latest != null ? latest + 1 : 1;
    }

    private List<MetaData> _getMetaData(String artifactId, Long version) {
        requireNonNull(artifactId);
        requireNonNull(version);

        return entityManager.createQuery(
            "SELECT m FROM MetaData m " +
            "WHERE m.artifactId = :artifact_id AND m.version = :version", MetaData.class)
                            .setParameter("artifact_id", artifactId)
                            .setParameter("version", version)
                            .getResultList();
    }

    private Rule _getRule(String artifactId, RuleType rule) {
        requireNonNull(rule); // TODO split null artifactId into separate method?
        try {
            TypedQuery<Rule> res = entityManager.createQuery(
                "SELECT r FROM Rule r " +
                "WHERE r.artifactId " + (artifactId == null ? "IS NULL" : "= :artifact_id") + " AND r.name = :name", Rule.class)
                                                .setParameter("name", rule);
            if (artifactId != null) {
                res.setParameter("artifact_id", artifactId);
            }
            return res.getSingleResult();
        } catch (NoResultException ex) {
            throw new RuleNotFoundException(rule, ex);
        }
    }

    private List<RuleConfig> _getRuleConfig(Rule rule) {
        requireNonNull(rule);
        return entityManager.createQuery("SELECT rc FROM RuleConfig rc " +
                                         "WHERE rc.rule.id = :rule_id", RuleConfig.class)
                            .setParameter("rule_id", rule.getId())
                            .getResultList();
    }

    private <T> T getMetaData(String artifactId, long version, String key, Function<MetaData, T> fn) {
        MetaData metaData = entityManager.createQuery(
            "SELECT m FROM MetaData m " +
            "WHERE m.artifactId = :artifact_id AND m.version = :version AND m.key = :key", MetaData.class)
                                         .setParameter("artifact_id", artifactId)
                                         .setParameter("version", version)
                                         .setParameter("key", key)
                                         .getSingleResult();
        return fn.apply(metaData);
    }

    private ArtifactState getState(String artifactId, long version) {
        return getMetaData(artifactId, version, MetaDataKeys.STATE, md -> ArtifactState.valueOf(md.getValue()));
    }

    private Artifact _getArtifact(String artifactId, long version, EnumSet<ArtifactState> states) {
        requireNonNull(artifactId);
        try {
            ArtifactState state = getState(artifactId, version);

            ArtifactStateExt.validateState(states, state, artifactId, version);

            return entityManager.createQuery("SELECT a FROM Artifact a " +
                                             "WHERE a.artifactId = :artifact_id " +
                                             "AND a.version = :version", Artifact.class)
                                .setParameter("artifact_id", artifactId)
                                .setParameter("version", version)
                                .getSingleResult();
        } catch (NoResultException ex) {
            throw new VersionNotFoundException(artifactId, version, ex);
        }
    }

    private Artifact _getArtifact(String artifactId, EnumSet<ArtifactState> states) {
        requireNonNull(artifactId);
        List<Artifact> artifacts;
        if (states != null) {
            List<String> statesQuery = states
                    .stream()
                    .map(s -> s.name())
                    .collect(Collectors.toList());
            String query =
                     "SELECT a.* " +
                     "FROM artifacts a " +
                     "INNER JOIN " +
                     "  (SELECT artifact_id, max(version) AS MaxVersion " +
                     "  FROM meta " +
                     "  WHERE artifact_id = :artifact_id AND key = :key AND value IN :states" +
                     "  GROUP BY artifact_id) b " +
                     "ON a.artifact_id = b.artifact_id AND a.version = b.MaxVersion";
            artifacts = entityManager.createNativeQuery(query, Artifact.class)
                    .setParameter("artifact_id", artifactId)
                    .setParameter("key", MetaDataKeys.STATE)
                    .setParameter("states", statesQuery)
                    .setMaxResults(1)
                    .getResultList();
        } else {
            artifacts = entityManager.createQuery(
                    "SELECT a FROM Artifact a " +
                            "WHERE a.artifactId = :artifact_id " +
                            "ORDER BY a.version DESC ", Artifact.class)
                    .setParameter("artifact_id", artifactId)
                    .setMaxResults(1)
                    .getResultList();
        }

        if (artifacts.isEmpty())
            throw new ArtifactNotFoundException(artifactId);
        else
            return artifacts.get(0);
    }

    private Artifact _getArtifact(long id, EnumSet<ArtifactState> states) {
        Artifact artifact = entityManager.createQuery(
            "SELECT a FROM Artifact a " +
            "WHERE a.globalId = :global_id", Artifact.class)
                                         .setParameter("global_id", id)
                                         .getSingleResult();

        ArtifactState state = getState(artifact.getArtifactId(), artifact.getVersion());

        ArtifactStateExt.validateState(states, state, artifact.getArtifactId(), artifact.getVersion());

        return artifact;
    }

    private void updateArtifactState(Artifact artifact, ArtifactState state) {
        if (state == ArtifactState.DELETED) {
            deleteArtifactVersionMetaData(artifact.getArtifactId(), artifact.getVersion());
            deleteArtifactVersion(artifact.getArtifactId(), artifact.getVersion());
        } else {
            MetaData md = getMetaData(artifact.getArtifactId(), artifact.getVersion(), MetaDataKeys.STATE, Function.identity());
            ArtifactState previousState = ArtifactState.valueOf(md.getValue());
            ArtifactStateExt.applyState(s -> md.setValue(s.name()), previousState, state);
        }
    }

    private void extractMetaData(ArtifactType artifactType, ContentHandle content, MetaDataMapperUpdater mdmu) {
        ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
        ContentExtractor extractor = provider.getContentExtractor();
        EditableMetaData emd = extractor.extract(content);
        if (extractor.isExtracted(emd)) {
            if (!isEmpty(emd.getName())) {
                mdmu.update(MetaDataKeys.NAME, emd.getName());
            }
            if (!isEmpty(emd.getDescription())) {
                mdmu.update(MetaDataKeys.DESCRIPTION, emd.getDescription());
            }
        }
    }

    private static String buildSearchAndClauseFromSearchOver(SearchOver searchOver, String search) {

        final String locateStringValueQuery = " UPPER(m2.value) like UPPER('%" + search + "%')";
        final String locateArtifactIdQuery = " UPPER(m2.artifact_id) like UPPER('%" + search + "%') ";

        switch (searchOver) {
            case description:
                return "AND (m2.key= 'description' AND " + locateStringValueQuery + ") ";
            case name:
                return "AND ((m2.key= 'name' AND " + locateStringValueQuery + ") " + " OR " + locateArtifactIdQuery + ") ";
            case labels:
                return "AND (m2.key= 'labels' AND " + locateStringValueQuery + ") ";
            default:
                return "AND (" + locateStringValueQuery + " OR " + locateArtifactIdQuery + ") ";
        }
    }

    private Query buildSearchArtifactQuery(String search, SearchOver over, SortOrder order, int offset, int limit) {

        final Query matchedArtifactsQuery = entityManager.createNativeQuery(
                "select * from (select m.artifact_id as id, "
                        + "MAX(case when m.key = '" + MetaDataKeys.NAME + "' then m.value end) as name,"
                        + "MAX(case when m.key = '" + MetaDataKeys.DESCRIPTION + "' then m.value end) as description, "
                        + "MAX(case when m.key = '" + MetaDataKeys.CREATED_ON + "' then m.value end) as createdOn, "
                        + "MAX(case when m.key = '" + MetaDataKeys.CREATED_BY + "' then m.value end) as createdBy, "
                        + "MAX(case when m.key = '" + MetaDataKeys.TYPE + "' then m.value end) as type,"
                        + "MAX(case when m.key = '" + MetaDataKeys.LABELS + "' then m.value end) as labels,"
                        + "MAX(case when m.key = '" + MetaDataKeys.STATE + "' then m.value end) as state, "
                        + "MAX(case when m.key = '" + MetaDataKeys.MODIFIED_ON + "' then m.value end) as modifiedOn,"
                        + "MAX(case when m.key = '" + MetaDataKeys.MODIFIED_BY + "' then m.value end) as modifiedBy "
                        + "from meta m "
                        + "where m.artifact_id || '===' || m.version  in "
                        + "(select m3.artifact_id || '===' || m3.version  from meta m3 where m3.version = "
                        + "(SELECT max(m2.version) from meta m2 where m.artifact_id = m2.artifact_id "
                        + (search == null ? "" : buildSearchAndClauseFromSearchOver(over, search))
                        + ") "
                        + ") group by m.artifact_id) searchResult"
                        + " order by coalesce(name, id) " + order.value(), "ArtifactSearchResultMapping");

        matchedArtifactsQuery.setFirstResult(offset);
        matchedArtifactsQuery.setMaxResults(limit);

        return matchedArtifactsQuery;
    }

    private SearchedArtifact buildSearchedArtifactFromResult(ArtifactSearchResult artifactSearchResult) {
        final SearchedArtifact searchedArtifact = new SearchedArtifact();
        searchedArtifact.setId(artifactSearchResult.getId());
        searchedArtifact.setName(artifactSearchResult.getName());
        searchedArtifact.setModifiedBy(artifactSearchResult.getModifiedBy());
        searchedArtifact.setModifiedOn(artifactSearchResult.getModifiedOn() != null ? Long.parseLong(artifactSearchResult.getModifiedOn()) : 0L);
        searchedArtifact.setCreatedBy(artifactSearchResult.getCreatedBy());
        searchedArtifact.setCreatedOn(artifactSearchResult.getCreatedOn() != null ? Long.parseLong(artifactSearchResult.getCreatedOn()) : 0L);
        searchedArtifact.setDescription(artifactSearchResult.getDescription());
        searchedArtifact.setState(ArtifactState.fromValue(artifactSearchResult.getState()));
        if (artifactSearchResult.getLabels() != null && !artifactSearchResult.getLabels().isEmpty()) {
            searchedArtifact.setLabels(Arrays.asList(artifactSearchResult.getLabels().split(",")));
        }
        searchedArtifact.setType(ArtifactType.fromValue(artifactSearchResult.getArtifactType()));

        return searchedArtifact;
    }

    // ========================================================================

    @Override
    @Transactional
    public boolean isAlive() {
        return (getGlobalRules() != null);
    }

    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state) {
        Artifact artifact = _getArtifact(artifactId, null);
        updateArtifactState(artifact, state);
    }

    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        Artifact artifact = _getArtifact(artifactId, version.longValue(), null);
        updateArtifactState(artifact, state);
    }

    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        requireNonNull(artifactId);
        requireNonNull(artifactType);
        requireNonNull(content);

        try {
            long nextVersion = _getNextArtifactVersion(artifactId);

            if (nextVersion != 1) {
                throw new ArtifactAlreadyExistsException(artifactId);
            }

            Artifact artifact = Artifact.builder()
                                        .artifactId(artifactId)
                                        .version(nextVersion)
                                        .content(content.bytes())
                                        .build();

            entityManager.persist(artifact);

            MetaDataMapperUpdater mdmu = new MetaDataMapperUpdater()
                .update(MetaDataKeys.STATE, ArtifactState.ENABLED.name())
                .update(MetaDataKeys.TYPE, artifactType.value());
            String currentTimeMillis = String.valueOf(System.currentTimeMillis());
            mdmu.update(MetaDataKeys.CREATED_ON, currentTimeMillis);
            mdmu.update(MetaDataKeys.MODIFIED_ON, currentTimeMillis);

            extractMetaData(artifactType, content, mdmu);

            ArtifactMetaDataDto amdd = mdmu.persistUpdate(entityManager, artifactId, nextVersion)
                                           .update(artifact)
                                           .toArtifactMetaDataDto();
            return CompletableFuture.completedFuture(amdd);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifact(artifactId, artifactType, content)
            .thenApply(amdd -> {
                updateArtifactMetaData(artifactId, metaData);
                return DtoUtil.setEditableMetaDataInArtifact(amdd, metaData);
            });
    }

    @Override
    @Transactional
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            List<Long> res1 = entityManager.createQuery(
                "SELECT a.version FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id " +
                "ORDER BY a.version DESC", Long.class)
                                           .setParameter("artifact_id", artifactId)
                                           .getResultList();

            if (res1.size() == 0) {
                throw new ArtifactNotFoundException(artifactId);
            }

            entityManager.createQuery(
                "DELETE FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id")
                         .setParameter("artifact_id", artifactId)
                         .executeUpdate();

            // delete meta data
            entityManager.createQuery(
                "DELETE FROM MetaData m " +
                "WHERE m.artifactId = :artifact_id")
                         .setParameter("artifact_id", artifactId)
                         .executeUpdate();

            // delete rules
            entityManager.createQuery(
                "DELETE FROM Rule r " +
                "WHERE r.artifactId = :artifact_id")
                         .setParameter("artifact_id", artifactId)
                         .executeUpdate();

            return new TreeSet<>(res1);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public StoredArtifact getArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            Artifact artifact = _getArtifact(artifactId, ArtifactStateExt.ACTIVE_STATES);

            return mapper.toStoredArtifact(artifact);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(artifactType);
            requireNonNull(content);

            long nextVersion = _getNextArtifactVersion(artifactId);
            if (nextVersion == 1L) {
                throw new ArtifactNotFoundException(artifactId);
            }

            Artifact artifact = Artifact.builder()
                                        .artifactId(artifactId)
                                        .version(nextVersion)
                                        .content(content.bytes())
                                        .build();

            entityManager.persist(artifact);

            String currentTimeMillis = String.valueOf(System.currentTimeMillis());

            MetaDataMapperUpdater mdmu = new MetaDataMapperUpdater()
                    .update(MetaDataKeys.STATE, ArtifactState.ENABLED.name())
                    .update(MetaDataKeys.TYPE, artifactType.value())
                    .update(MetaDataKeys.CREATED_ON, currentTimeMillis)
                    .update(MetaDataKeys.MODIFIED_ON, currentTimeMillis)
                    // copy name and description .. if previous version (still) exists
                    .update(_getMetaData(artifactId, nextVersion - 1), MetaDataKeys.NAME,
                            MetaDataKeys.DESCRIPTION);

            extractMetaData(artifactType, content, mdmu);

            ArtifactMetaDataDto amdd = mdmu.persistUpdate(entityManager, artifactId, nextVersion)
                                           .update(artifact)
                                           .toArtifactMetaDataDto();

            if (amdd.getVersion() != ARTIFACT_FIRST_VERSION) {
                ArtifactVersionMetaDataDto firstVersionContent = getArtifactVersionMetaData(artifactId, ARTIFACT_FIRST_VERSION);
                amdd.setCreatedOn(firstVersionContent.getCreatedOn());
            }

            amdd.setModifiedOn(Long.parseLong(currentTimeMillis));

            return CompletableFuture.completedFuture(amdd);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        return updateArtifact(artifactId, artifactType, content)
            .thenApply(amdd -> {
                updateArtifactMetaData(artifactId, metaData);
                return DtoUtil.setEditableMetaDataInArtifact(amdd, metaData);
            });
    }

    @Override
    @Transactional
    public Set<String> getArtifactIds(Integer limit) {
        try {
            TypedQuery<String> idsQuery = entityManager.createQuery("SELECT a.artifactId FROM Artifact a", String.class);

            if (limit != null) {
                idsQuery.setMaxResults(limit);
            }
            return new HashSet<>(idsQuery.getResultList());
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public ArtifactSearchResults searchArtifacts(String search, int offset,
            int limit, SearchOver searchOver, SortOrder sortOrder) {

        final String countQuery =
                "SELECT count ( distinct m.artifact_id)  FROM meta m "
                        + "WHERE m.version = "
                        + "(SELECT max(m2.version) "
                        + "FROM meta m2 WHERE m.artifact_id = m2.artifact_id "
                        + (search == null ? "" : buildSearchAndClauseFromSearchOver(searchOver, search))
                        + " )"
                        + " AND m.artifact_id IN (select a.artifact_id from artifacts a) ";

        final Query count = entityManager.createNativeQuery(countQuery);

        final Query matchedArtifactsQuery = buildSearchArtifactQuery(search, searchOver, sortOrder, offset, limit);

        matchedArtifactsQuery.setFirstResult(offset);
        matchedArtifactsQuery.setMaxResults(limit);

        final List<ArtifactSearchResult> matchedResults = matchedArtifactsQuery.getResultList();

        final List<SearchedArtifact> searchedArtifacts = matchedResults
                .stream()
                .map(this::buildSearchedArtifactFromResult)
                .collect(Collectors.toList());

        final ArtifactSearchResults searchResults = new ArtifactSearchResults();

        searchResults.setCount(((Number) count.getSingleResult()).intValue());
        searchResults.setArtifacts(searchedArtifacts);
        return searchResults;
    }

    // =======================================================

    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            Artifact artifact = _getArtifact(artifactId, ArtifactStateExt.ACTIVE_STATES);

            final ArtifactMetaDataDto artifactMetaDataDto = new MetaDataMapperUpdater(_getMetaData(artifactId, artifact.getVersion()))
                .update(artifact)
                .toArtifactMetaDataDto();

            if (artifactMetaDataDto.getVersion() != ARTIFACT_FIRST_VERSION) {
                final ArtifactVersionMetaDataDto artifactVersionMetaDataDto = getArtifactVersionMetaData(artifactId, ARTIFACT_FIRST_VERSION);
                artifactMetaDataDto.setCreatedOn(artifactVersionMetaDataDto.getCreatedOn());
            }

            final SortedSet<Long> versions = getArtifactVersions(artifactId);

            if (artifactMetaDataDto.getVersion() != versions.last()) {
                final ArtifactVersionMetaDataDto artifactVersionMetaDataDto = getArtifactVersionMetaData(artifactId, versions.last());
                artifactMetaDataDto.setModifiedOn(artifactVersionMetaDataDto.getCreatedOn());
            }

            return artifactMetaDataDto;
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            // Get the meta-data for the artifact
            ArtifactMetaDataDto metaData = getArtifactMetaData(artifactId);

            // Create a canonicalizer for the artifact based on its type, and then
            // canonicalize the inbound content
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(metaData.getType());
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            byte[] canonicalBytes = canonicalContent.bytes();

            Artifact artifact = null;
            List<Artifact> list = entityManager.createQuery(
                "SELECT a FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id " +
                "ORDER BY a.version DESC ", Artifact.class)
                                               .setParameter("artifact_id", artifactId)
                                               .getResultList();
            for (Artifact candidateArtifact : list) {
                ContentHandle candidateContent = ContentHandle.create(candidateArtifact.getContent());
                ContentHandle canonicalCandidateContent = canonicalizer.canonicalize(candidateContent);
                byte[] candidateBytes = canonicalCandidateContent.bytes();
                if (Arrays.equals(canonicalBytes, candidateBytes)) {
                    artifact = candidateArtifact;
                    break;
                }
            }

            if (artifact == null) {
                throw new ArtifactNotFoundException(artifactId);
            }

            ArtifactMetaDataDto artifactMetaDataDto = new MetaDataMapperUpdater(
                    _getMetaData(artifactId, artifact.getVersion()))
                    .update(artifact)
                    .toArtifactMetaDataDto();

            artifactMetaDataDto.setCreatedOn(metaData.getCreatedOn());
            artifactMetaDataDto.setModifiedOn(metaData.getModifiedOn());

            return artifactMetaDataDto;
        } catch (PersistenceException e) {
            throw new RegistryStorageException(e);
        }
    }

    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            Artifact artifact = _getArtifact(id, null);

            return new MetaDataMapperUpdater(_getMetaData(artifact.getArtifactId(), artifact.getVersion()))
                .update(artifact)
                .toArtifactMetaDataDto();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(metaData);

            Artifact artifact = _getArtifact(artifactId, ArtifactStateExt.ACTIVE_STATES);

            new MetaDataMapperUpdater(_getMetaData(artifactId, artifact.getVersion()))
                .update(metaData)
                .persistUpdate(entityManager, artifactId, artifact.getVersion());
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public List<RuleType> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _getArtifact(artifactId, null);

            return entityManager.createQuery("SELECT r.name FROM Rule r " +
                                             "WHERE r.artifactId = :artifact_id", RuleType.class)
                                .setParameter("artifact_id", artifactId)
                                .getResultList();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public CompletionStage<Void> createArtifactRuleAsync(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(rule);
            requireNonNull(config);

            if (getArtifactRules(artifactId).contains(rule)) {
                throw new RuleAlreadyExistsException(rule);
            }

            Rule ruleEntity = Rule.builder()
                                  .artifactId(artifactId)
                                  .name(rule)
                                  .build();

            entityManager.persist(ruleEntity);

            new RuleConfigMapperUpdater()
                .update(config)
                .persistUpdate(entityManager, ruleEntity);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    @Transactional
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _getArtifact(artifactId, null);

            entityManager.createQuery("DELETE FROM Rule r " +
                                      "WHERE r.artifactId = :artifact_id")
                         .setParameter("artifact_id", artifactId)
                         .executeUpdate();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Transactional
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
    throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(rule);

            Rule res1 = _getRule(artifactId, rule);

            return new RuleConfigMapperUpdater(_getRuleConfig(res1))
                .toRuleConfigurationDto();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
    throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(rule);
            requireNonNull(config);

            Rule ruleEntity = _getRule(artifactId, rule);

            new RuleConfigMapperUpdater(_getRuleConfig(ruleEntity))
                .update(config)
                .persistUpdate(entityManager, ruleEntity);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public void deleteArtifactRule(String artifactId, RuleType rule)
    throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(rule);

            _getArtifact(artifactId, null);

            int affected = entityManager.createQuery("DELETE FROM Rule r " +
                                                     "WHERE r.artifactId = :artifact_id AND r.name = :name")
                                        .setParameter("artifact_id", artifactId)
                                        .setParameter("name", rule)
                                        .executeUpdate();

            if (affected == 0) {
                throw new RuleNotFoundException(rule);
            }
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            List<Long> versions = entityManager.createQuery(
                "SELECT a.version FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id " +
                "ORDER BY a.version DESC", Long.class)
                                               .setParameter("artifact_id", artifactId)
                                               .getResultList();

            if (versions.isEmpty()) {
                throw new ArtifactNotFoundException(artifactId);
            }

            return new TreeSet<>(versions);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, int offset, int limit) {

        final VersionSearchResults versionSearchResults = new VersionSearchResults();
        final LongAdder itemsCount = new LongAdder();

        final List<SearchedVersion> versions = getArtifactVersions(artifactId).stream()
                .peek(version -> itemsCount.increment())
                .sorted(Long::compareTo)
                .skip(offset)
                .limit(limit)
                .map(version -> SearchUtil.buildSearchedVersion(getArtifactVersionMetaData(artifactId, version)))
                .collect(Collectors.toList());

        versionSearchResults.setVersions(versions);
        versionSearchResults.setCount(itemsCount.intValue());

        return versionSearchResults;
    }

    @Override
    @Transactional
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        try {
            Artifact artifact = _getArtifact(id, ArtifactStateExt.ACTIVE_STATES);

            return mapper.toStoredArtifact(artifact);
        } catch (NoResultException ex) {
            throw new ArtifactNotFoundException(ex);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public StoredArtifact getArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        requireNonNull(artifactId);
        try {
            return mapper.toStoredArtifact(_getArtifact(artifactId, version, ArtifactStateExt.ACTIVE_STATES));
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteArtifactVersion(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        requireNonNull(artifactId);
        try {
            // TODO ArtifactVersionNotFoundEx ?
            int affected = entityManager.createQuery("DELETE FROM Artifact a " +
                                                     "WHERE a.artifactId = :artifact_id AND a.version = :version")
                                        .setParameter("artifact_id", artifactId)
                                        .setParameter("version", version)
                                        .executeUpdate();
            if (affected == 0) {
                throw new VersionNotFoundException(artifactId, version);
            }
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        requireNonNull(artifactId);
        try {
            Artifact artifact = _getArtifact(artifactId, version, null);

            return new MetaDataMapperUpdater(_getMetaData(artifactId, version))
                .update(artifact)
                .toArtifactVersionMetaDataDto();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void updateArtifactVersionMetaData(String artifactId, long version, EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        requireNonNull(artifactId);
        requireNonNull(metaData);
        try {
            _getArtifact(artifactId, version, ArtifactStateExt.ACTIVE_STATES);

            new MetaDataMapperUpdater(_getMetaData(artifactId, version))
                .update(metaData)
                .persistUpdate(entityManager, artifactId, version);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _getArtifact(artifactId, null);

            entityManager.createQuery("DELETE FROM MetaData md " +
                                      "WHERE md.artifactId = :artifact_id AND md.version = :version")
                         .setParameter("artifact_id", artifactId)
                         .setParameter("version", version)
                         .executeUpdate();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public List<RuleType> getGlobalRules() throws RegistryStorageException {
        try {
            return entityManager.createQuery("SELECT r.name FROM Rule r " +
                                             "WHERE r.artifactId IS NULL", RuleType.class)
                                .getResultList();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
    throws RuleAlreadyExistsException, RegistryStorageException {
        requireNonNull(rule);
        requireNonNull(config);
        try {

            if (getGlobalRules().contains(rule))
                throw new RuleAlreadyExistsException(rule);

            Rule ruleEntity = Rule.builder()
                                  .artifactId(null)
                                  .name(rule)
                                  .build();

            entityManager.persist(ruleEntity);

            new RuleConfigMapperUpdater()
                .update(config)
                .persistUpdate(entityManager, ruleEntity);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteGlobalRules() throws RegistryStorageException {
        try {
            entityManager.createQuery("DELETE FROM Rule r " +
                                      "WHERE r.artifactId IS NULL")
                         .executeUpdate();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public RuleConfigurationDto getGlobalRule(RuleType rule)
    throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(rule);
        try {
            Rule ruleEntity = _getRule(null, rule);
            return new RuleConfigMapperUpdater(_getRuleConfig(ruleEntity))
                .toRuleConfigurationDto();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    @Transactional
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
    throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(rule);
        requireNonNull(config);
        try {
            Rule ruleEntity = _getRule(null, rule);
            new RuleConfigMapperUpdater(_getRuleConfig(ruleEntity))
                .update(config)
                .persistUpdate(entityManager, ruleEntity)
                .toRuleConfigurationDto();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Transactional
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(rule);
        try {
            Rule ruleEntity = _getRule(null, rule);
            entityManager.remove(ruleEntity);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }
}
