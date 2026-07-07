package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.xregistry.rest.v1.RootResource;
import io.apicurio.registry.xregistry.rest.v1.beans.RegistryEntity;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import jakarta.interceptor.Interceptors;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class RootResourceImpl extends AbstractXRegistryResource implements RootResource {

    private static final String SPEC_VERSION = "1.0-rc3";
    private static final String REGISTRY_ID = "apicurio-registry";

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public RegistryEntity getRootDocument(String inline, List<String> filter, Boolean doc,
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
        GroupSearchResultsDto groupResults = storage.searchGroups(
                Collections.emptySet(), OrderBy.name, OrderDirection.asc, 0, 1);
        entity.setSchemagroupscount(groupResults.getCount());
        return entity;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public RegistryEntity upsertDocument(String inline, List<String> filter, Boolean doc,
            String specversion, RegistryEntity data) {
        throw notImplemented();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public RegistryEntity patchDocument(String inline, List<String> filter, Boolean doc,
            String specversion, RegistryEntity data) {
        throw notImplemented();
    }
}
