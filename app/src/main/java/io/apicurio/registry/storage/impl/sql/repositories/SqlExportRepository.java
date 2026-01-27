package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.BranchEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.CommentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.ContentEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GlobalRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GroupRuleEntityMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StringMapper;
import io.apicurio.registry.utils.StringUtil;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Repository handling export and snapshot operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlExportRepository {

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
    @ConfigProperty(name = "apicurio.events.kafka.topic", defaultValue = "registry-events")
    String eventsTopic;

    /**
     * Export all content entities.
     */
    public void exportContent(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<ContentEntity> stream = handle.createQuery(sqlStatements.exportContent()).setFetchSize(50)
                    .map(ContentEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all group entities.
     */
    public void exportGroups(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<GroupEntity> stream = handle.createQuery(sqlStatements.exportGroups()).setFetchSize(50)
                    .map(GroupEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all group rule entities.
     */
    public void exportGroupRules(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<GroupRuleEntity> stream = handle.createQuery(sqlStatements.exportGroupRules())
                    .setFetchSize(50).map(GroupRuleEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all artifact entities.
     */
    public void exportArtifacts(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<ArtifactEntity> stream = handle.createQuery(sqlStatements.exportArtifacts())
                    .setFetchSize(50).map(ArtifactEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all artifact version entities.
     */
    public void exportArtifactVersions(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<ArtifactVersionEntity> stream = handle.createQuery(sqlStatements.exportArtifactVersions())
                    .setFetchSize(50).map(ArtifactVersionEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all version comment entities.
     */
    public void exportVersionComments(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<CommentEntity> stream = handle.createQuery(sqlStatements.exportVersionComments())
                    .setFetchSize(50).map(CommentEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all branch entities with their version numbers.
     */
    public void exportBranches(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<BranchEntity> stream = handle.createQuery(sqlStatements.exportBranches()).setFetchSize(50)
                    .map(BranchEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(branch -> {
                    branch.versions = getBranchVersionNumbersRaw(branch);
                    handler.apply(branch);
                });
            }
            return null;
        });
    }

    private List<String> getBranchVersionNumbersRaw(BranchEntity branch) {
        return handles.withHandleNoException(handle -> {
            return handle.createQuery(sqlStatements.selectBranchVersionNumbers())
                    .bind(0, branch.groupId == null ? "__$GROUPID$__" : branch.groupId)
                    .bind(1, branch.artifactId).bind(2, branch.branchId).map(StringMapper.instance).list();
        });
    }

    /**
     * Export all artifact rule entities.
     */
    public void exportArtifactRules(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<ArtifactRuleEntity> stream = handle.createQuery(sqlStatements.exportArtifactRules())
                    .setFetchSize(50).map(ArtifactRuleEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Export all global rule entities.
     */
    public void exportGlobalRules(Function<Entity, Void> handler) {
        handles.withHandle(handle -> {
            Stream<GlobalRuleEntity> stream = handle.createQuery(sqlStatements.exportGlobalRules())
                    .setFetchSize(50).map(GlobalRuleEntityMapper.instance).stream();
            try (stream) {
                stream.forEach(handler::apply);
            }
            return null;
        });
    }

    /**
     * Create a database snapshot.
     */
    public String createSnapshot(String location) throws RegistryStorageException {
        if (!StringUtil.isEmpty(location)) {
            log.debug("Creating internal database snapshot to location {}.", location);
            handles.withHandleNoException(handle -> {
                handle.createQuery(sqlStatements.createDataSnapshot()).bind(0, location).mapTo(String.class)
                        .first();
            });
            return location;
        } else {
            log.warn("Skipping database snapshot because no location has been provided");
        }
        return null;
    }

    /**
     * Create an outbox event.
     */
    public String createEvent(OutboxEvent event) {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.createOutboxEvent()).bind(0, event.getId())
                    .bind(1, eventsTopic).bind(2, event.getAggregateId()).bind(3, event.getType())
                    .bind(4, event.getPayload().toString()).execute();

            return handle.createUpdate(sqlStatements.deleteOutboxEvent()).bind(0, event.getId())
                    .execute();
        });
        return event.getId();
    }
}
