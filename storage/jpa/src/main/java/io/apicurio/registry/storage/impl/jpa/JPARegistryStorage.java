/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.content.ContentCanonicalizer;
import io.apicurio.registry.content.ContentCanonicalizerFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactStateExt;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.MetaDataKeys;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.jpa.entity.Artifact;
import io.apicurio.registry.storage.impl.jpa.entity.MetaData;
import io.apicurio.registry.storage.impl.jpa.entity.Rule;
import io.apicurio.registry.storage.impl.jpa.entity.RuleConfig;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;

@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
public class JPARegistryStorage implements RegistryStorage {

    @Inject
    ContentCanonicalizerFactory ccFactory;

    @Inject
    EntityManager entityManager;

    @Inject
    JPAEntityMapper mapper;

    private long _getNextArtifactVersion(String artifactId) {
        requireNonNull(artifactId);
        Long latest = entityManager.createQuery(
                "SELECT MAX(a.version) FROM Artifact a " +
                        "WHERE a.artifactId = :artifact_id", Long.class)
                .setParameter("artifact_id", artifactId)
                .getSingleResult();
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
            TypedQuery<Rule> res = entityManager.createQuery("SELECT r FROM Rule r " +
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

    private Artifact _getArtifact(String artifactId, long version, EnumSet<ArtifactState> states) {
        requireNonNull(artifactId);
        try {
            Artifact artifact = entityManager.createQuery("SELECT a FROM Artifact a " +
                                                          "WHERE a.artifactId = :artifact_id " +
                                                          "AND a.version = :version", Artifact.class)
                                             .setParameter("artifact_id", artifactId)
                                             .setParameter("version", version)
                                             .getSingleResult();

            ArtifactStateExt.validateState(states, artifact.getState(), artifactId, version);

            return artifact;
        } catch (NoResultException ex) {
            throw new VersionNotFoundException(artifactId, version, ex);
        }
    }

    private Artifact _getArtifact(String artifactId, EnumSet<ArtifactState> states) {
        requireNonNull(artifactId);
        try {
            Artifact artifact = entityManager.createQuery(
                "SELECT a FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id " +
                "AND a.state in (:states) " +
                "ORDER BY a.version DESC ", Artifact.class)
                                             .setParameter("artifact_id", artifactId)
                                             .setParameter("states", states)
                                             .setMaxResults(1)
                                             .getSingleResult();

            ArtifactStateExt.logIfDeprecated(artifactId, artifact.getState(), artifact.getVersion());

            return artifact;
        } catch (NoResultException ex) {
            throw new ArtifactNotFoundException(artifactId, ex);
        }
    }

    private boolean _artifactExists(String artifactId, EnumSet<ArtifactState> states) {
        requireNonNull(artifactId);
        return entityManager.createQuery("SELECT COUNT(a) FROM Artifact a " +
                                         "WHERE a.artifactId = :artifact_id " +
                                         "AND a.state in (:states)", Long.class)
                            .setParameter("artifact_id", artifactId)
                            .setParameter("states", states)
                            .getSingleResult() != 0;
    }

    private void _ensureArtifactExists(String artifactId, EnumSet<ArtifactState> states) {
        if (!_artifactExists(artifactId, states))
            throw new ArtifactNotFoundException(artifactId);
    }

    private Artifact _getArtifact(long id, EnumSet<ArtifactState> states) {
        Artifact artifact = entityManager.createQuery(
            "SELECT a FROM Artifact a " +
            "WHERE a.globalId = :global_id", Artifact.class)
                                          .setParameter("global_id", id)
                                          .getSingleResult();

        ArtifactStateExt.validateState(states, artifact.getState(), artifact.getArtifactId(), artifact.getVersion());

        return artifact;
    }

    private void updateArtifactState(Artifact artifact, ArtifactState state) {
        if (state == ArtifactState.DELETED) {
            deleteArtifactVersion(artifact.getArtifactId(), artifact.getVersion());
        } else {
            ArtifactStateExt.applyState(artifact::setState, artifact.getState(), state);
        }
    }

    // ========================================================================

    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state) {
        Artifact artifact = _getArtifact(artifactId, ArtifactStateExt.ALL);
        updateArtifactState(artifact, state);
    }

    @Override
    @Transactional
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        Artifact artifact = _getArtifact(artifactId, version.longValue(), ArtifactStateExt.ALL);
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
                                        .state(ArtifactState.ENABLED)
                                        .build();

            entityManager.persist(artifact);

            ArtifactMetaDataDto amdd = new MetaDataMapperUpdater()
                .update(MetaDataKeys.TYPE, artifactType.value())
                .persistUpdate(entityManager, artifactId, nextVersion)
                .update(artifact)
                .toArtifactMetaDataDto();
            return CompletableFuture.completedFuture(amdd);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
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
                                        .state(ArtifactState.ENABLED)
                                        .build();

            entityManager.persist(artifact);

            ArtifactMetaDataDto amdd = new MetaDataMapperUpdater()
                .update(MetaDataKeys.TYPE, artifactType.value())
                // copy name and description .. if previous version (still) exists
                .update(_getMetaData(artifactId, nextVersion - 1), MetaDataKeys.NAME, MetaDataKeys.DESCRIPTION)
                .persistUpdate(entityManager, artifactId, nextVersion)
                .update(artifact)
                .toArtifactMetaDataDto();
            return CompletableFuture.completedFuture(amdd);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public Set<String> getArtifactIds() {
        try {
            List<String> ids = entityManager.createQuery(
                "SELECT a.artifactId FROM Artifact a", String.class)
                                            .getResultList();

            return new HashSet<>(ids);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    // =======================================================

    @Override
    @Transactional
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            Artifact artifact = _getArtifact(artifactId, ArtifactStateExt.ALL);

            return new MetaDataMapperUpdater(_getMetaData(artifactId, artifact.getVersion()))
                .update(artifact)
                .toArtifactMetaDataDto();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#getArtifactMetaData(java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            
            // Get the meta-data for the artifact
            ArtifactMetaDataDto metaData = getArtifactMetaData(artifactId);

            // Create a canonicalizer for the artifact based on its type, and then 
            // canonicalize the inbound content
            ContentCanonicalizer canonicalizer = ccFactory.create(metaData.getType());
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            byte[] canonicalBytes = canonicalContent.bytes();

            Artifact artifact = null;
            List<Artifact> list = entityManager.createQuery(
                    "SELECT a FROM Artifact a " +
                    "WHERE a.artifactId = :artifact_id " +
                    "AND a.state in (:states) " +
                    "ORDER BY a.version DESC ", Artifact.class)
               .setParameter("artifact_id", artifactId)
               .setParameter("states", ArtifactStateExt.ALL)
               .getResultList();
            for (Artifact candidateArtifact : list) {
                ContentHandle candidateContent = ContentHandle.create(candidateArtifact.getContent());
                ContentHandle canonicalCandidateContent = canonicalizer.canonicalize(candidateContent);
                byte[] candidateBytes = canonicalCandidateContent.bytes();
                if (Arrays.equals(canonicalBytes, candidateBytes)) {
                    artifact = candidateArtifact;
                }
            }

            if (artifact == null) {
                throw new ArtifactNotFoundException(artifactId);
            }

            return new MetaDataMapperUpdater(_getMetaData(artifactId, artifact.getVersion()))
                .update(artifact)
                .toArtifactMetaDataDto();
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            Artifact artifact = _getArtifact(id, ArtifactStateExt.ALL);

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

            _ensureArtifactExists(artifactId, ArtifactStateExt.ALL);

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
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
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
    }

    @Override
    @Transactional
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _ensureArtifactExists(artifactId, ArtifactStateExt.ALL);

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

            _ensureArtifactExists(artifactId, ArtifactStateExt.ALL);

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
            Artifact artifact = _getArtifact(artifactId, version, ArtifactStateExt.ALL);

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
            _ensureArtifactExists(artifactId, ArtifactStateExt.ACTIVE_STATES);

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

            _ensureArtifactExists(artifactId, ArtifactStateExt.ALL);

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
