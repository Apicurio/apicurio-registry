package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.xregistry.rest.v1.CapabilitiesResource;
import io.apicurio.registry.xregistry.rest.v1.beans.RegistryCapabilities;
import jakarta.interceptor.Interceptors;

import java.util.List;

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class CapabilitiesResourceImpl extends AbstractXRegistryResource
        implements CapabilitiesResource {

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public RegistryCapabilities getCapabilities(Boolean offered, String specversion) {
        RegistryCapabilities caps = new RegistryCapabilities();
        caps.setPagination(true);
        caps.setSticky(true);
        caps.setShortself(false);
        caps.setEnforcecompatibility(false);
        caps.setSpecversions(List.of("1.0-rc3"));
        caps.setSchemas(List.of("xRegistry-json/1.0-rc3"));
        caps.setFlags(List.of());
        caps.setIgnore(List.of());
        caps.setMutable(List.of("name", "description", "documentation", "labels",
                "format", "compatibility", "defaultversionid", "defaultversionsticky",
                "readonly"));
        return caps;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public RegistryCapabilities updateCapabilities(String specversion,
            RegistryCapabilities data) {
        throw methodNotAllowed();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public RegistryCapabilities patchCapabilities(String specversion,
            RegistryCapabilities data) {
        throw methodNotAllowed();
    }
}
