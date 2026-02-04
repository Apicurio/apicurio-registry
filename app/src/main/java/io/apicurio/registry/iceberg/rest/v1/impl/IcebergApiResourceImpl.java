package io.apicurio.registry.iceberg.rest.v1.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.iceberg.rest.v1.ApisResource;
import io.apicurio.registry.iceberg.rest.v1.beans.CatalogConfig;
import io.apicurio.registry.iceberg.rest.v1.beans.Config;
import io.apicurio.registry.iceberg.rest.v1.beans.CreateNamespaceRequest;
import io.apicurio.registry.iceberg.rest.v1.beans.CreateNamespaceResponse;
import io.apicurio.registry.iceberg.rest.v1.beans.CreateTableRequest;
import io.apicurio.registry.iceberg.rest.v1.beans.Defaults;
import io.apicurio.registry.iceberg.rest.v1.beans.GetNamespaceResponse;
import io.apicurio.registry.iceberg.rest.v1.beans.ListNamespacesResponse;
import io.apicurio.registry.iceberg.rest.v1.beans.ListTablesResponse;
import io.apicurio.registry.iceberg.rest.v1.beans.LoadTableResponse;
import io.apicurio.registry.iceberg.rest.v1.beans.Overrides;
import io.apicurio.registry.iceberg.rest.v1.beans.Properties;
import io.apicurio.registry.iceberg.rest.v1.beans.RenameTableRequest;
import io.apicurio.registry.iceberg.rest.v1.beans.TableIdentifier;
import io.apicurio.registry.iceberg.rest.v1.beans.TableMetadata;
import io.apicurio.registry.iceberg.rest.v1.beans.UpdateNamespacePropertiesRequest;
import io.apicurio.registry.iceberg.rest.v1.beans.UpdateNamespacePropertiesResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.GroupNotEmptyException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the Iceberg REST Catalog API.
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class IcebergApiResourceImpl implements ApisResource {

    private static final String NAMESPACE_SEPARATOR = "\u0000";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    IcebergConfig icebergConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public CatalogConfig getConfig() {
        CatalogConfig config = new CatalogConfig();

        Defaults defaults = new Defaults();
        if (icebergConfig.getDefaultWarehouse() != null && !icebergConfig.getDefaultWarehouse().isEmpty()) {
            defaults.setAdditionalProperty("warehouse", icebergConfig.getDefaultWarehouse());
        }
        defaults.setAdditionalProperty("prefix", icebergConfig.getDefaultPrefix());

        Overrides overrides = new Overrides();
        overrides.setAdditionalProperty("uri", "");

        config.setDefaults(defaults);
        config.setOverrides(overrides);

        return config;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ListNamespacesResponse listNamespaces(String prefix, String parent, String pageToken,
            BigInteger pageSize) {

        int limit = pageSize != null ? pageSize.intValue() : 100;
        int offset = 0;
        if (pageToken != null && !pageToken.isEmpty()) {
            try {
                offset = Integer.parseInt(pageToken);
            } catch (NumberFormatException e) {
                // Invalid page token, start from beginning
            }
        }

        Set<SearchFilter> filters = new HashSet<>();
        if (parent != null && !parent.isEmpty()) {
            String parentGroupId = namespaceToGroupId(parent);
            filters.add(SearchFilter.ofGroupId(parentGroupId + ".*"));
        }

        GroupSearchResultsDto results = storage.searchGroups(filters, OrderBy.groupId, OrderDirection.asc,
                offset, limit);

        List<List<String>> namespaces = results.getGroups().stream()
                .map(g -> groupIdToNamespace(g.getId()))
                .collect(Collectors.toList());

        ListNamespacesResponse response = new ListNamespacesResponse();
        response.setNamespaces(namespaces);

        if (results.getCount() > offset + limit) {
            response.setNextPageToken(String.valueOf(offset + limit));
        }

        return response;
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public CreateNamespaceResponse createNamespace(String prefix, CreateNamespaceRequest data) {
        List<String> namespace = data.getNamespace();
        String groupId = namespaceToGroupId(namespace);

        Properties requestProperties = data.getProperties();
        Map<String, String> propsMap = new HashMap<>();
        if (requestProperties != null) {
            for (Map.Entry<String, Object> entry : requestProperties.getAdditionalProperties().entrySet()) {
                propsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        GroupMetaDataDto dto = GroupMetaDataDto.builder()
                .groupId(groupId)
                .description(propsMap.get("comment"))
                .labels(propsMap)
                .owner(getCurrentUser())
                .createdOn(System.currentTimeMillis())
                .modifiedOn(System.currentTimeMillis())
                .modifiedBy(getCurrentUser())
                .build();

        storage.createGroup(dto);

        CreateNamespaceResponse response = new CreateNamespaceResponse();
        response.setNamespace(namespace);
        response.setProperties(requestProperties);

        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public GetNamespaceResponse loadNamespaceMetadata(String prefix, String namespace) {
        String groupId = namespaceToGroupId(namespace);
        GroupMetaDataDto group = storage.getGroupMetaData(groupId);

        GetNamespaceResponse response = new GetNamespaceResponse();
        response.setNamespace(groupIdToNamespace(group.getGroupId()));

        Properties properties = new Properties();
        if (group.getLabels() != null) {
            for (Map.Entry<String, String> entry : group.getLabels().entrySet()) {
                properties.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
        if (group.getDescription() != null) {
            properties.setAdditionalProperty("comment", group.getDescription());
        }
        response.setProperties(properties);

        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public void namespaceExists(String prefix, String namespace) {
        String groupId = namespaceToGroupId(namespace);
        storage.getGroupMetaData(groupId);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Admin)
    public void dropNamespace(String prefix, String namespace) {
        String groupId = namespaceToGroupId(namespace);

        // Check if namespace has tables by searching
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(groupId));
        ArtifactSearchResultsDto artifacts = storage.searchArtifacts(filters, OrderBy.artifactId,
                OrderDirection.asc, 0, 1);

        if (artifacts.getCount() > 0) {
            throw new GroupNotEmptyException(groupId, artifacts.getCount());
        }

        storage.deleteGroup(groupId);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public UpdateNamespacePropertiesResponse updateNamespaceProperties(String prefix, String namespace,
            UpdateNamespacePropertiesRequest data) {

        String groupId = namespaceToGroupId(namespace);
        GroupMetaDataDto group = storage.getGroupMetaData(groupId);

        Map<String, String> currentLabels = group.getLabels() != null ? new HashMap<>(group.getLabels())
                : new HashMap<>();

        List<String> updated = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (data.getRemovals() != null) {
            for (String key : data.getRemovals()) {
                if (currentLabels.containsKey(key)) {
                    currentLabels.remove(key);
                    removed.add(key);
                } else {
                    missing.add(key);
                }
            }
        }

        if (data.getUpdates() != null) {
            for (Map.Entry<String, Object> entry : data.getUpdates().getAdditionalProperties().entrySet()) {
                currentLabels.put(entry.getKey(), String.valueOf(entry.getValue()));
                updated.add(entry.getKey());
            }
        }

        String description = currentLabels.get("comment");
        io.apicurio.registry.storage.dto.EditableGroupMetaDataDto editableDto =
                io.apicurio.registry.storage.dto.EditableGroupMetaDataDto.builder()
                        .description(description)
                        .labels(currentLabels)
                        .build();
        storage.updateGroupMetaData(groupId, editableDto);

        UpdateNamespacePropertiesResponse response = new UpdateNamespacePropertiesResponse();
        response.setUpdated(updated);
        response.setRemoved(removed);
        response.setMissing(missing);

        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public ListTablesResponse listTables(String prefix, String namespace, String pageToken,
            BigInteger pageSize) {

        String groupId = namespaceToGroupId(namespace);

        int limit = pageSize != null ? pageSize.intValue() : 100;
        int offset = 0;
        if (pageToken != null && !pageToken.isEmpty()) {
            try {
                offset = Integer.parseInt(pageToken);
            } catch (NumberFormatException e) {
                // Invalid page token, start from beginning
            }
        }

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(groupId));
        filters.add(SearchFilter.ofArtifactType(ArtifactType.ICEBERG_TABLE));

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.artifactId,
                OrderDirection.asc, offset, limit);

        List<TableIdentifier> identifiers = results.getArtifacts().stream()
                .map(a -> {
                    TableIdentifier id = new TableIdentifier();
                    id.setNamespace(groupIdToNamespace(a.getGroupId()));
                    id.setName(a.getArtifactId());
                    return id;
                })
                .collect(Collectors.toList());

        ListTablesResponse response = new ListTablesResponse();
        response.setIdentifiers(identifiers);

        if (results.getCount() > offset + limit) {
            response.setNextPageToken(String.valueOf(offset + limit));
        }

        return response;
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public LoadTableResponse createTable(String prefix, String namespace, String xIcebergAccessDelegation,
            CreateTableRequest data) {

        String groupId = namespaceToGroupId(namespace);
        String tableName = data.getName();

        String tableUuid = UUID.randomUUID().toString();
        String location = data.getLocation();
        if (location == null || location.isEmpty()) {
            String warehouse = icebergConfig.getDefaultWarehouse();
            if (warehouse == null || warehouse.isEmpty()) {
                warehouse = "/warehouse";
            }
            location = warehouse + "/" + groupId.replace(".", "/") + "/" + tableName;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("format-version", 2);
        metadata.put("table-uuid", tableUuid);
        metadata.put("location", location);
        metadata.put("last-sequence-number", 0);
        metadata.put("last-updated-ms", System.currentTimeMillis());
        metadata.put("last-column-id", 0);
        metadata.put("current-schema-id", 0);
        metadata.put("schemas", List.of(data.getSchema()));
        metadata.put("default-spec-id", 0);
        metadata.put("partition-specs", data.getPartitionSpec() != null
                ? List.of(data.getPartitionSpec())
                : List.of(Map.of("spec-id", 0, "fields", List.of())));
        metadata.put("last-partition-id", 999);
        metadata.put("default-sort-order-id", 0);
        metadata.put("sort-orders", data.getWriteOrder() != null
                ? List.of(data.getWriteOrder())
                : List.of(Map.of("order-id", 0, "fields", List.of())));

        Map<String, Object> tableProps = new HashMap<>();
        if (data.getProperties() != null) {
            tableProps.putAll(data.getProperties().getAdditionalProperties());
        }
        metadata.put("properties", tableProps);
        metadata.put("current-snapshot-id", -1);
        metadata.put("snapshots", List.of());
        metadata.put("snapshot-log", List.of());
        metadata.put("metadata-log", List.of());
        metadata.put("refs", Map.of());

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize table metadata", e);
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("table-uuid", tableUuid);
        labels.put("location", location);
        if (data.getProperties() != null) {
            for (Map.Entry<String, Object> entry : data.getProperties().getAdditionalProperties().entrySet()) {
                labels.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                .labels(labels)
                .build();
        EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder()
                .build();
        ContentWrapperDto content = ContentWrapperDto.builder()
                .content(ContentHandle.create(metadataJson))
                .contentType(ContentTypes.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        storage.createArtifact(groupId, tableName, ArtifactType.ICEBERG_TABLE, artifactMetaData, null,
                content, versionMetaData, null, false, false, getCurrentUser());

        LoadTableResponse response = new LoadTableResponse();
        response.setMetadataLocation(location + "/metadata/v1.metadata.json");

        TableMetadata tableMetadata = new TableMetadata();
        tableMetadata.setFormatVersion(2);
        tableMetadata.setTableUuid(tableUuid);
        tableMetadata.setLocation(location);
        tableMetadata.setSchemas(List.of(data.getSchema()));
        tableMetadata.setCurrentSchemaId(0);
        tableMetadata.setProperties(data.getProperties());

        response.setMetadata(tableMetadata);
        response.setConfig(new Config());

        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public LoadTableResponse loadTable(String prefix, String namespace, String table,
            String xIcebergAccessDelegation, String snapshots) {

        String groupId = namespaceToGroupId(namespace);

        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(groupId, table,
                storage.getBranchTip(new GA(groupId, table), BranchId.LATEST,
                        RetrievalBehavior.SKIP_DISABLED_LATEST).getRawVersionId());

        String metadataJson = artifact.getContent().content();

        TableMetadata metadata;
        try {
            metadata = objectMapper.readValue(metadataJson, TableMetadata.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse table metadata", e);
        }

        LoadTableResponse response = new LoadTableResponse();
        response.setMetadata(metadata);
        response.setMetadataLocation(metadata.getLocation() + "/metadata/v1.metadata.json");
        response.setConfig(new Config());

        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public void tableExists(String prefix, String namespace, String table) {
        String groupId = namespaceToGroupId(namespace);
        storage.getArtifactMetaData(groupId, table);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Admin)
    public void dropTable(String prefix, String namespace, String table, Boolean purgeRequested) {
        String groupId = namespaceToGroupId(namespace);
        storage.deleteArtifact(groupId, table);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public void renameTable(String prefix, RenameTableRequest data) {
        TableIdentifier source = data.getSource();
        TableIdentifier destination = data.getDestination();

        String sourceGroupId = namespaceToGroupId(source.getNamespace());
        String sourceTable = source.getName();
        String destGroupId = namespaceToGroupId(destination.getNamespace());
        String destTable = destination.getName();

        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(sourceGroupId, sourceTable,
                storage.getBranchTip(new GA(sourceGroupId, sourceTable), BranchId.LATEST,
                        RetrievalBehavior.SKIP_DISABLED_LATEST).getRawVersionId());

        String metadataJson = artifact.getContent().content();

        if (!sourceGroupId.equals(destGroupId) || !sourceTable.equals(destTable)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
                String oldLocation = (String) metadata.get("location");
                if (oldLocation != null) {
                    String warehouse = icebergConfig.getDefaultWarehouse();
                    if (warehouse == null || warehouse.isEmpty()) {
                        warehouse = "/warehouse";
                    }
                    String newLocation = warehouse + "/" + destGroupId.replace(".", "/") + "/" + destTable;
                    metadata.put("location", newLocation);
                    metadataJson = objectMapper.writeValueAsString(metadata);
                }
            } catch (Exception e) {
                // Keep original metadata if parsing fails
            }
        }

        ContentWrapperDto content = ContentWrapperDto.builder()
                .content(ContentHandle.create(metadataJson))
                .contentType(ContentTypes.APPLICATION_JSON)
                .references(Collections.emptyList())
                .build();

        storage.createArtifact(destGroupId, destTable, ArtifactType.ICEBERG_TABLE,
                EditableArtifactMetaDataDto.builder().build(), null, content,
                EditableVersionMetaDataDto.builder().build(), null, false, false, getCurrentUser());

        storage.deleteArtifact(sourceGroupId, sourceTable);
    }

    private String namespaceToGroupId(List<String> namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return null;
        }
        return String.join(".", namespace);
    }

    private List<String> groupIdToNamespace(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(groupId.split("\\."));
    }

    private String namespaceToGroupId(String encodedNamespace) {
        if (encodedNamespace == null || encodedNamespace.isEmpty()) {
            return null;
        }
        String decoded = URLDecoder.decode(encodedNamespace, StandardCharsets.UTF_8);
        List<String> parts = Arrays.asList(decoded.split(NAMESPACE_SEPARATOR));
        return namespaceToGroupId(parts);
    }

    private String getCurrentUser() {
        return securityIdentity.getPrincipal().getName();
    }
}
