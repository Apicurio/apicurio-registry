package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.xregistry.rest.v1.ExportResource;
import io.apicurio.registry.xregistry.rest.v1.beans.RegistryEntity;
import io.apicurio.registry.xregistry.rest.v1.beans.Schemagroups;
import jakarta.interceptor.Interceptors;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ExportResourceImpl extends AbstractXRegistryResource implements ExportResource {

    private static final String SPEC_VERSION = "1.0-rc3";
    private static final String REGISTRY_ID = "apicurio-registry";
    private static final int MAX_EXPORT_GROUPS = 10000;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public RegistryEntity exportRegistry(Boolean doc, String inline, List<String> filter,
            String specversion) {
        URI basePath = buildSelfUri("");
        RegistryEntity entity = new RegistryEntity();
        entity.setSpecversion(SPEC_VERSION);
        entity.setRegistryid(REGISTRY_ID);
        entity.setEpoch(0);
        entity.setName("Apicurio Registry");
        entity.setDescription("Apicurio Registry - xRegistry API");
        if (basePath != null) {
            entity.setSelf(basePath.resolve("/apis/xregistry/v1/"));
            entity.setXid(basePath.resolve("/apis/xregistry/v1/").toString());
            entity.setSchemagroupsurl(
                    basePath.resolve("/apis/xregistry/v1/schemagroups"));
        }
        Date now = new Date();
        entity.setCreatedat(now);
        entity.setModifiedat(now);

        // Inline all schema groups
        GroupSearchResultsDto groupResults = storage.searchGroups(
                Collections.emptySet(), OrderBy.name, OrderDirection.asc,
                0, MAX_EXPORT_GROUPS);
        entity.setSchemagroupscount(groupResults.getCount());

        Schemagroups schemagroupsMap = new Schemagroups();
        for (SearchedGroupDto group : groupResults.getGroups()) {
            schemagroupsMap.setAdditionalProperty(group.getId(),
                    XRegistryApiConverter.toSchemagroup(
                            storage.getGroupMetaData(group.getId()), basePath));
        }
        entity.setSchemagroups(schemagroupsMap);

        return entity;
    }
}
