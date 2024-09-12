package io.apicurio.registry.auth;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.NotFoundException;
import io.apicurio.registry.types.Current;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;

public abstract class AbstractAccessController implements IAccessController {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @Current
    RegistryStorage storage;

    protected boolean isOwner(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedStyle style = annotation.style();

        if (style == AuthorizedStyle.GroupAndArtifact) {
            String groupId = getStringParam(context, 0);
            String artifactId = getStringParam(context, 1);
            return verifyArtifactOwner(groupId, artifactId);
        } else if (style == AuthorizedStyle.GroupOnly
                && authConfig.ownerOnlyAuthorizationLimitGroupAccess.get()) {
            String groupId = getStringParam(context, 0);
            return verifyGroupOwner(groupId);
        } else if (style == AuthorizedStyle.ArtifactOnly) {
            String artifactId = getStringParam(context, 0);
            return verifyArtifactOwner(null, artifactId);
        } else if (style == AuthorizedStyle.GlobalId) {
            long globalId = getLongParam(context, 0);
            return verifyArtifactOwner(globalId);
        } else {
            return true;
        }
    }

    private boolean verifyGroupOwner(String groupId) {
        try {
            GroupMetaDataDto dto = storage.getGroupMetaData(groupId);
            String owner = dto.getOwner();
            return owner == null || owner.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the group is not found, then return true and let the operation proceed.
            return true;
        }
    }

    private boolean verifyArtifactOwner(String groupId, String artifactId) {
        try {
            ArtifactMetaDataDto dto = storage.getArtifactMetaData(groupId, artifactId);
            String owner = dto.getOwner();
            return owner == null || owner.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the artifact is not found, then return true and let the operation proceed
            // as normal. The result of which will typically be a 404 response, but sometimes
            // will be some other result (e.g. creating an artifact that doesn't exist)
            return true;
        }
    }

    private boolean verifyArtifactOwner(long globalId) {
        try {
            ArtifactVersionMetaDataDto versionMetaData = storage.getArtifactVersionMetaData(globalId);
            ArtifactMetaDataDto dto = storage.getArtifactMetaData(versionMetaData.getGroupId(),
                    versionMetaData.getArtifactId());
            String owner = dto.getOwner();
            return owner == null || owner.equals(securityIdentity.getPrincipal().getName());
        } catch (NotFoundException nfe) {
            // If the artifact is not found, then return true and let the operation proceed
            // as normal. The result of which will typically be a 404 response, but sometimes
            // will be some other result (e.g. creating an artifact that doesn't exist)
            return true;
        }
    }

    protected String getStringParam(InvocationContext context, int index) {
        return (String) context.getParameters()[index];
    }

    protected Long getLongParam(InvocationContext context, int index) {
        return (Long) context.getParameters()[index];
    }

}
