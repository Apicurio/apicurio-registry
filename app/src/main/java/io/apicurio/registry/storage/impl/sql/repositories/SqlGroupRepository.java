package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.SqlStatementVariableBinder;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.jdb.Query;
import io.apicurio.registry.storage.impl.sql.mappers.GroupMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.SearchedGroupMapper;
import io.apicurio.registry.events.GroupCreated;
import io.apicurio.registry.events.GroupDeleted;
import io.apicurio.registry.events.GroupMetadataUpdated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.utils.StringUtil.limitStr;
import static io.apicurio.registry.utils.StringUtil.asLowerCase;

/**
 * Repository handling group operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlGroupRepository {

    public static final int MAX_LABEL_KEY_LENGTH = 256;
    public static final int MAX_LABEL_VALUE_LENGTH = 512;

    // Internal default group ID representation (duplicated from RegistryContentUtils for access)
    private static final String NULL_GROUP_ID = "__$GROUPID$__";

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
    SecurityIdentity securityIdentity;

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

    @Inject
    RestConfig restConfig;

    /**
     * Create a new group.
     */
    public void createGroup(GroupMetaDataDto group)
            throws GroupAlreadyExistsException, RegistryStorageException {
        // Prevent creation of groups with the internal default group ID representation
        if (NULL_GROUP_ID.equals(group.getGroupId())) {
            throw new RegistryStorageException("Invalid group ID: '" + NULL_GROUP_ID
                    + "' is a reserved internal identifier.");
        }

        try {
            handles.withHandle(handle -> {
                String modifiedBy = group.getModifiedBy();
                if (modifiedBy == null || modifiedBy.isEmpty()) {
                    modifiedBy = group.getOwner();
                }

                Date modifiedOn;
                if (group.getModifiedOn() == 0) {
                    modifiedOn = group.getCreatedOn() == 0 ? new Date() : new Date(group.getCreatedOn());
                } else {
                    modifiedOn = new Date(group.getModifiedOn());
                }

                handle.createUpdate(sqlStatements.insertGroup()).bind(0, group.getGroupId())
                        .bind(1, group.getDescription()).bind(2, group.getArtifactsType())
                        .bind(3, group.getOwner())
                        .bind(4, group.getCreatedOn() == 0 ? new Date() : new Date(group.getCreatedOn()))
                        .bind(5, modifiedBy)
                        .bind(6, modifiedOn)
                        .bind(7, RegistryContentUtils.serializeLabels(group.getLabels())).execute();

                // Insert labels
                Map<String, String> labels = group.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertGroupLabel())
                                .bind(0, group.getGroupId())
                                .bind(1, limitStr(k.toLowerCase(), MAX_LABEL_KEY_LENGTH))
                                .bind(2, limitStr(asLowerCase(v), MAX_LABEL_VALUE_LENGTH)).execute();
                    });
                }

                outboxEvent.fire(SqlOutboxEvent.of(GroupCreated.of(group)));

                return null;
            });
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new GroupAlreadyExistsException(group.getGroupId());
            }
            throw ex;
        }
    }

    /**
     * Delete a group and all its artifacts.
     */
    public void deleteGroup(String groupId) throws GroupNotFoundException, RegistryStorageException {
        handles.withHandleNoException(handle -> {
            // Delete artifact rules
            handle.createUpdate(sqlStatements.deleteArtifactRulesByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            // Delete all artifacts in the group
            handle.createUpdate(sqlStatements.deleteArtifactsByGroupId()).bind(0, normalizeGroupId(groupId))
                    .execute();

            // Delete the group
            int rows = handle.createUpdate(sqlStatements.deleteGroup()).bind(0, groupId).execute();
            if (rows == 0) {
                throw new GroupNotFoundException(groupId);
            }

            outboxEvent.fire(SqlOutboxEvent.of(GroupDeleted.of(groupId)));

            return null;
        });
    }

    /**
     * Get group metadata.
     */
    public GroupMetaDataDto getGroupMetaData(String groupId)
            throws GroupNotFoundException, RegistryStorageException {
        return handles.withHandle(handle -> {
            Optional<GroupMetaDataDto> res = handle.createQuery(sqlStatements.selectGroupByGroupId())
                    .bind(0, groupId).map(GroupMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new GroupNotFoundException(groupId));
        });
    }

    /**
     * Update group metadata.
     */
    public void updateGroupMetaData(String groupId, EditableGroupMetaDataDto dto) {
        log.debug("Updating metadata for group {}.", groupId);

        handles.withHandleNoException(handle -> {
            boolean modified = false;

            // Update description
            if (dto.getDescription() != null) {
                int rowCount = handle.createUpdate(sqlStatements.updateGroupDescription())
                        .bind(0, dto.getDescription())
                        .bind(1, groupId)
                        .execute();
                modified = true;
                if (rowCount == 0) {
                    throw new GroupNotFoundException(groupId);
                }
            }

            // Update owner
            if (dto.getOwner() != null && !dto.getOwner().trim().isEmpty()) {
                int rowCount = handle.createUpdate(sqlStatements.updateGroupOwner())
                        .bind(0, dto.getOwner())
                        .bind(1, groupId)
                        .execute();
                modified = true;
                if (rowCount == 0) {
                    throw new GroupNotFoundException(groupId);
                }
            }

            // Update labels
            if (dto.getLabels() != null) {
                int rowCount = handle.createUpdate(sqlStatements.updateGroupLabels())
                        .bind(0, RegistryContentUtils.serializeLabels(dto.getLabels()))
                        .bind(1, groupId)
                        .execute();
                modified = true;
                if (rowCount == 0) {
                    throw new GroupNotFoundException(groupId);
                }

                // Delete all appropriate rows in the "group_labels" table
                handle.createUpdate(sqlStatements.deleteGroupLabelsByGroupId()).bind(0, groupId).execute();

                // Insert new labels into the "group_labels" table
                if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
                    dto.getLabels().forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertGroupLabel())
                                .bind(0, groupId)
                                .bind(1, limitStr(k.toLowerCase(), 256))
                                .bind(2, limitStr(asLowerCase(v), 512))
                                .execute();
                    });
                }
            }

            // Update modifiedBy and modifiedOn if anything changed
            if (modified) {
                String modifiedBy = securityIdentity.getPrincipal().getName();
                Date modifiedOn = new Date();

                int rowCount = handle.createUpdate(sqlStatements.updateGroupModifiedByOn())
                        .bind(0, modifiedBy)
                        .bind(1, modifiedOn)
                        .bind(2, groupId)
                        .execute();
                if (rowCount == 0) {
                    throw new GroupNotFoundException(groupId);
                }

                outboxEvent.fire(SqlOutboxEvent.of(GroupMetadataUpdated.of(groupId, dto)));
            }

            return null;
        });
    }

    /**
     * Get all group IDs.
     */
    public List<String> getGroupIds(Integer limit) throws RegistryStorageException {
        return handles.withHandleNoException(handle -> {
            Query query = handle.createQuery(sqlStatements.selectGroups());
            query.bind(0, limit);
            return query.map(rs -> rs.getString("groupId")).list();
        });
    }

    /**
     * Check if a group exists.
     */
    public boolean isGroupExists(String groupId) throws RegistryStorageException {
        return handles.<Boolean, RuntimeException>withHandleNoException(
                handle -> isGroupExistsRaw(handle, groupId));
    }

    /**
     * Check if a group exists using an existing handle.
     */
    public boolean isGroupExistsRaw(Handle handle, String groupId) throws RegistryStorageException {
        return handle.createQuery(sqlStatements.selectGroupCountById()).bind(0, normalizeGroupId(groupId))
                .mapTo(Integer.class).one() > 0;
    }

    /**
     * Search for groups.
     */
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, Integer offset, Integer limit) {
        return handles.withHandleNoException(handle -> {
            List<SqlStatementVariableBinder> binders = new LinkedList<>();
            String op;

            StringBuilder where = new StringBuilder();
            StringBuilder orderByQuery = new StringBuilder();

            // Build WHERE clause
            where.append(" WHERE (1 = 1)");
            for (SearchFilter filter : filters) {
                where.append(" AND (");
                switch (filter.getType()) {
                    case description:
                        op = filter.isNot() ? "NOT LIKE" : "LIKE";
                        where.append("g.description ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, "%" + filter.getStringValue() + "%");
                        });
                        break;
                    case groupId:
                        op = filter.isNot() ? "!=" : "=";
                        where.append("g.groupId ");
                        where.append(op);
                        where.append(" ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, filter.getStringValue());
                        });
                        break;
                    case labels:
                        op = filter.isNot() ? "!=" : "=";
                        Pair<String, String> label = filter.getLabelFilterValue();
                        String labelKey = label.getKey().toLowerCase();
                        where.append("EXISTS(SELECT l.* FROM group_labels l WHERE l.labelKey " + op + " ?");
                        binders.add((query, idx) -> {
                            query.bind(idx, labelKey);
                        });
                        if (label.getValue() != null) {
                            String labelValue = label.getValue().toLowerCase();
                            where.append(" AND l.labelValue " + op + " ?");
                            binders.add((query, idx) -> {
                                query.bind(idx, labelValue);
                            });
                        }
                        where.append(" AND l.groupId = g.groupId)");
                        break;
                    default:
                        throw new RegistryStorageException("Filter type not supported: " + filter.getType());
                }
                where.append(")");
            }

            // Build ORDER BY
            switch (orderBy) {
                case groupId:
                case createdOn:
                case modifiedOn:
                    orderByQuery.append(" ORDER BY g.").append(orderBy.name());
                    break;
                default:
                    throw new RuntimeException("Sort by " + orderBy.name() + " not supported.");
            }
            orderByQuery.append(" ").append(orderDirection.name());

            // Build queries
            String groupsQuerySql = sqlStatements.selectTableTemplate("*", "groups", "g", where.toString(),
                    orderByQuery.toString());
            Query groupsQuery = handle.createQuery(groupsQuerySql);

            String countQuerySql = sqlStatements.selectCountTableTemplate("g.groupId", "groups", "g",
                    where.toString());
            Query countQuery = handle.createQuery(countQuerySql);

            // Bind parameters
            int idx = 0;
            for (SqlStatementVariableBinder binder : binders) {
                binder.bind(groupsQuery, idx);
                binder.bind(countQuery, idx);
                idx++;
            }

            if ("mssql".equals(sqlStatements.dbType())) {
                groupsQuery.bind(idx++, offset);
                groupsQuery.bind(idx++, limit);
            } else {
                groupsQuery.bind(idx++, limit);
                groupsQuery.bind(idx++, offset);
            }

            // Execute queries
            List<SearchedGroupDto> groups = groupsQuery.map(SearchedGroupMapper.instance).list();
            limitReturnedLabelsInGroups(groups);
            Integer count = countQuery.mapTo(Integer.class).one();

            GroupSearchResultsDto results = new GroupSearchResultsDto();
            results.setGroups(groups);
            results.setCount(count);
            return results;
        });
    }

    /**
     * Ensure a group exists (create if not exists).
     */
    public void ensureGroup(GroupMetaDataDto group) {
        try {
            createGroup(group);
        } catch (GroupAlreadyExistsException e) {
            // This is OK - we're happy if the group already exists.
        }
    }

    // ==================== IMPORT OPERATIONS ====================

    /**
     * Import a group entity (used for data import/migration).
     */
    public void importGroup(GroupEntity entity) {
        handles.withHandleNoException(handle -> {
            if (isGroupExistsRaw(handle, entity.groupId)) {
                throw new GroupAlreadyExistsException(entity.groupId);
            }

            handle.createUpdate(sqlStatements.importGroup())
                    .bind(0, normalizeGroupId(entity.groupId))
                    .bind(1, entity.description)
                    .bind(2, entity.artifactsType)
                    .bind(3, entity.owner)
                    .bind(4, new Date(entity.createdOn))
                    .bind(5, entity.modifiedBy)
                    .bind(6, new Date(entity.modifiedOn))
                    .bind(7, RegistryContentUtils.serializeLabels(entity.labels))
                    .execute();

            // Insert labels into the "group_labels" table
            if (entity.labels != null && !entity.labels.isEmpty()) {
                entity.labels.forEach((k, v) -> {
                    handle.createUpdate(sqlStatements.insertGroupLabel())
                            .bind(0, normalizeGroupId(entity.groupId))
                            .bind(1, k.toLowerCase())
                            .bind(2, v.toLowerCase())
                            .execute();
                });
            }

            return null;
        });
    }

    /**
     * Limit the size of labels returned in search results.
     */
    private Map<String, String> limitReturnedLabels(Map<String, String> labels) {
        int maxBytes = restConfig.getLabelsInSearchResultsMaxSize();
        if (labels != null && !labels.isEmpty()) {
            Map<String, String> cappedLabels = new HashMap<>();
            int totalBytes = 0;
            for (String key : labels.keySet()) {
                if (totalBytes < maxBytes) {
                    String value = labels.get(key);
                    cappedLabels.put(key, value);
                    totalBytes += key.length() + (value != null ? value.length() : 0);
                }
            }
            return cappedLabels;
        }
        return labels;
    }

    /**
     * Limit labels in group search results.
     */
    private void limitReturnedLabelsInGroups(List<SearchedGroupDto> groups) {
        groups.forEach(group -> {
            Map<String, String> labels = group.getLabels();
            Map<String, String> cappedLabels = limitReturnedLabels(labels);
            group.setLabels(cappedLabels);
        });
    }
}
