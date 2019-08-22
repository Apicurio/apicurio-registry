package io.apicurio.registry.storage.impl.jpa;

import io.apicurio.registry.rest.beans.ArtifactType;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;

@JPA
@ApplicationScoped
public class JPARegistryStorage implements RegistryStorage {

    private static Logger log = LoggerFactory.getLogger(JPARegistryStorage.class);

    @Inject
    private EntityManager entityManager;

    @Inject
    private JPAEntityMapper mapper;

    private long _getNextArtifactVersion(String artifactId) {
        Long latest = entityManager.createQuery(
                "SELECT MAX(a.version) FROM Artifact a " +
                        "WHERE a.artifactId = :artifact_id", Long.class)
                .setParameter("artifact_id", artifactId)
                .getSingleResult();
        return latest != null ? latest + 1 : 1;
    }

    private List<MetaData> _getMetaData(String artifactId, Long version) {
        TypedQuery<MetaData> res = entityManager.createQuery(
                "SELECT m FROM MetaData m " +
                        "WHERE m.artifactId = :artifact_id AND m.version " + (version == null ? "IS NULL" : "= :version"), MetaData.class)
                .setParameter("artifact_id", artifactId);
        if (version != null)
            res.setParameter("version", version);
        return res.getResultList();
    }

    private Rule _getRule(String artifactId, String ruleName) {
        return entityManager.createQuery("SELECT r FROM Rule r " +
                "WHERE r.artifactId = :artifact_id AND r.name = :name", Rule.class)
                .setParameter("artifact_id", artifactId)
                .setParameter("name", ruleName)
                .getSingleResult();
    }

    private List<RuleConfig> _getRuleConfig(Rule rule) {
        return entityManager.createQuery("SELECT rc FROM RuleConfig rc " +
                "WHERE rc.rule.id = :rule_id", RuleConfig.class)
                .setParameter("rule_id", rule.getId())
                .getResultList();
    }

    private Artifact _getArtifact(String artifactId, long version) {
        return entityManager.createQuery("SELECT a FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id AND a.version = :version", Artifact.class)
                .setParameter("artifact_id", artifactId)
                .setParameter("version", version)
                .getSingleResult();
    }

    private Artifact _getArtifact(String artifactId) {
        return entityManager.createQuery(
                "SELECT  a FROM Artifact a " +
                        "WHERE a.artifactId = :artifact_id " +
                        "ORDER BY a.version DESC ", Artifact.class)
                .setParameter("artifact_id", artifactId)
                .setMaxResults(1)
                .getSingleResult();
    }


    private boolean _artifactExists(String artifactId) {
        return entityManager.createQuery("SELECT COUNT(a) FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id", Long.class)
                .setParameter("artifact_id", artifactId)
                .getSingleResult() != 0;
    }

    private void _ensureArtifactExists(String artifactId) {
        if (!_artifactExists(artifactId))
            throw new ArtifactNotFoundException(artifactId);
    }

    private boolean _artifactVersionExists(String artifactId, long version) {
        return entityManager.createQuery("SELECT COUNT(a) FROM Artifact a " +
                "WHERE a.artifactId = :artifact_id AND a.version = :version", Long.class)
                .setParameter("artifact_id", artifactId)
                .setParameter("version", version)
                .getSingleResult() != 0;
    }

    // ========================================================================

    @Override
    @Transactional
    public ArtifactMetaDataDto createArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactAlreadyExistsException, RegistryStorageException {
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
                    .content(content)
                    .build();

            entityManager.persist(artifact);

            new MetaDataMapperUpdater()
                    .update(MetaDataKeys.TYPE, artifactType.value())
                    .persistUpdate(entityManager, artifactId, nextVersion);

            return new MetaDataMapperUpdater()
                    .update(MetaDataKeys.TYPE, artifactType.value())
                    .persistUpdate(entityManager, artifactId, null)
                    .update(artifact)
                    .toArtifactMetaDataDto();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public SortedSet<Long> deleteArtifact(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _ensureArtifactExists(artifactId);

            List<Long> res1 = entityManager.createQuery(
                    "SELECT a.version FROM Artifact a " +
                            "WHERE a.artifactId = :artifact_id " +
                            "ORDER BY a.version DESC", Long.class)
                    .setParameter("artifact_id", artifactId)
                    .getResultList();

            if (res1.size() == 0)
                throw new ArtifactNotFoundException(artifactId);

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

            Artifact artifact = _getArtifact(artifactId);

            return mapper.toStoredArtifact(artifact);

        } catch (NoResultException ex) {
            throw new ArtifactNotFoundException(artifactId, ex);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public ArtifactMetaDataDto updateArtifact(String artifactId, ArtifactType artifactType, String content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(artifactType);
            requireNonNull(content);

            long nextVersion = _getNextArtifactVersion(artifactId);

            if (nextVersion == 1) {
                throw new ArtifactNotFoundException(artifactId);
            }

            Artifact artifact = Artifact.builder()
                    .artifactId(artifactId)
                    .version(nextVersion)
                    .content(content)
                    .build();

            entityManager.persist(artifact);

            new MetaDataMapperUpdater()
                    .update(MetaDataKeys.TYPE, artifactType.value())
                    .persistUpdate(entityManager, artifactId, nextVersion);

            return new MetaDataMapperUpdater()
                    .update(MetaDataKeys.TYPE, artifactType.value())
                    .persistUpdate(entityManager, artifactId, null)
                    .update(artifact)
                    .toArtifactMetaDataDto();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public Set<String> getArtifactIds() {
        try {
            List<String> res1 = entityManager.createQuery(
                    "SELECT a.artifactId FROM Artifact a", String.class)
                    .getResultList();

            return new HashSet<>(res1);

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

            Artifact artifact = _getArtifact(artifactId);

            return new MetaDataMapperUpdater(_getMetaData(artifactId, null))
                    .update(artifact)
                    .toArtifactMetaDataDto();

        } catch (NoResultException ex) {
            throw new ArtifactNotFoundException(artifactId, ex);
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

            _ensureArtifactExists(artifactId);

            new MetaDataMapperUpdater(_getMetaData(artifactId, null))
                    .update(metaData)
                    .persistUpdate(entityManager, artifactId, null);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public List<String> getArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _ensureArtifactExists(artifactId);

            return entityManager.createQuery("SELECT r.name FROM Rule r " +
                    "WHERE r.artifactId = :artifact_id", String.class)
                    .setParameter("artifact_id", artifactId)
                    .getResultList();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void createArtifactRule(String artifactId, String ruleName, RuleConfigurationDto config) throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(ruleName);
            requireNonNull(config);

            if (getArtifactRules(artifactId).contains(ruleName))
                throw new RuleAlreadyExistsException(ruleName);

            Rule rule = Rule.builder()
                    .artifactId(artifactId)
                    .name(ruleName)
                    .build();

            entityManager.persist(rule);

            new RuleConfigMapperUpdater()
                    .update(config)
                    .persistUpdate(entityManager, rule);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteArtifactRules(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _ensureArtifactExists(artifactId);

            entityManager.createQuery("DELETE FROM Rule r " +
                    "WHERE r.artifactId = :artifact_id")
                    .setParameter("artifact_id", artifactId)
                    .executeUpdate();

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public RuleConfigurationDto getArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(ruleName);

            Rule res1 = _getRule(artifactId, ruleName);

            return new RuleConfigMapperUpdater(_getRuleConfig(res1))
                    .toRuleConfigurationDto();

        } catch (EntityNotFoundException ex) {
            throw new RuleNotFoundException(ruleName, ex);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void updateArtifactRule(String artifactId, String ruleName, RuleConfigurationDto ruleConfiguration) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(ruleName);
            requireNonNull(ruleConfiguration);

            Rule rule = _getRule(artifactId, ruleName);

            new RuleConfigMapperUpdater(_getRuleConfig(rule))
                    .update(ruleConfiguration)
                    .persistUpdate(entityManager, rule);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteArtifactRule(String artifactId, String ruleName) throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);
            requireNonNull(ruleName);

            _ensureArtifactExists(artifactId);

            int affected = entityManager.createQuery("DELETE FROM Rule r " +
                    "WHERE r.artifactId = :artifact_id AND r.name = :name", Rule.class)
                    .setParameter("artifact_id", artifactId)
                    .setParameter("name", ruleName)
                    .executeUpdate();

            if (affected == 0)
                throw new RuleNotFoundException(ruleName);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public SortedSet<Long> getArtifactVersions(String artifactId) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            requireNonNull(artifactId);

            _ensureArtifactExists(artifactId);

            List<Long> res1 = entityManager.createQuery(
                    "SELECT a.version FROM Artifact a " +
                            "WHERE a.artifactId = :artifact_id " +
                            "ORDER BY a.version DESC", Long.class)
                    .setParameter("artifact_id", artifactId)
                    .getResultList();

            return new TreeSet(res1);

        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public StoredArtifact getArtifactVersion(long id) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        try {
            Artifact artifact = entityManager.createQuery(
                    "SELECT a FROM Artifact a " +
                            "WHERE a.globalId = :global_id", Artifact.class)
                    .setParameter("global_id", id)
                    .getSingleResult();

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
            // TODO ArtifactVersionNotFoundEx ?
            if (!_artifactVersionExists(artifactId, version))
                throw new VersionNotFoundException(artifactId, version);

            return mapper.toStoredArtifact(_getArtifact(artifactId, version));

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
                    "WHERE a.artifactId = :artifact_id AND a.version = :version", Artifact.class)
                    .setParameter("artifact_id", artifactId)
                    .setParameter("version", version)
                    .executeUpdate();
            if (affected == 0)
                throw new VersionNotFoundException(artifactId, version);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        requireNonNull(artifactId);
        try {
            Artifact artifact = _getArtifact(artifactId, version);

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
            _ensureArtifactExists(artifactId);

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

            _ensureArtifactExists(artifactId);

            entityManager.createQuery("DELETE FROM MetaData md " +
                    "WHERE md.artifactId = :artifact_id AND md.version = :version", MetaData.class)
                    .setParameter("artifact_id", artifactId)
                    .setParameter("version", version)
                    .executeUpdate();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public List<String> getGlobalRules() throws RegistryStorageException {
        try {
            return entityManager.createQuery("SELECT r.name FROM Rule r " +
                    "WHERE r.artifactId IS NULL", String.class)
                    .getResultList();
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void createGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleAlreadyExistsException, RegistryStorageException {
        requireNonNull(ruleName);
        requireNonNull(config);
        try {

            if (getGlobalRules().contains(ruleName))
                throw new RuleAlreadyExistsException(ruleName);

            Rule rule = Rule.builder()
                    .artifactId(null)
                    .name(ruleName)
                    .build();

            entityManager.persist(rule);

            new RuleConfigMapperUpdater()
                    .update(config)
                    .persistUpdate(entityManager, rule);

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

    @Override
    @Transactional
    public RuleConfigurationDto getGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(ruleName);
        try {
            Rule rule = _getRule(null, ruleName);
            return new RuleConfigMapperUpdater(_getRuleConfig(rule))
                    .toRuleConfigurationDto();
        } catch (NoResultException ex) {
            throw new RuleNotFoundException(ruleName);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void updateGlobalRule(String ruleName, RuleConfigurationDto config) throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(ruleName);
        requireNonNull(config);
        try {
            Rule rule = _getRule(null, ruleName);
            new RuleConfigMapperUpdater(_getRuleConfig(rule))
                    .update(config)
                    .toRuleConfigurationDto();

        } catch (NoResultException ex) {
            throw new RuleNotFoundException(ruleName);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }

    @Override
    @Transactional
    public void deleteGlobalRule(String ruleName) throws RuleNotFoundException, RegistryStorageException {
        requireNonNull(ruleName);
        try {
            Rule rule = _getRule(null, ruleName);
            entityManager.remove(rule);
        } catch (PersistenceException ex) {
            throw new RegistryStorageException(ex);
        }
    }
}
