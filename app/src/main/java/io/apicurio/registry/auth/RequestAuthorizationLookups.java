package io.apicurio.registry.auth;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import jakarta.enterprise.context.RequestScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lookups remembered for the current request; see {@link AuthorizationLookups}.
 */
@RequestScoped
class RequestAuthorizationLookups {

    // Accessed only through methods: this bean is injected as a client proxy, and reading a field
    // of the proxy would read the proxy's own copy, shared by every request, not this request's
    private final Map<List<String>, Optional<ArtifactMetaDataDto>> artifacts = new ConcurrentHashMap<>();
    private final Map<String, Optional<GroupMetaDataDto>> groups = new ConcurrentHashMap<>();
    private final Map<Long, Optional<ArtifactVersionMetaDataDto>> versions = new ConcurrentHashMap<>();
    private final Map<Long, Optional<List<ArtifactVersionMetaDataDto>>> contentUsers = new ConcurrentHashMap<>();

    Map<List<String>, Optional<ArtifactMetaDataDto>> artifacts() {
        return artifacts;
    }

    Map<String, Optional<GroupMetaDataDto>> groups() {
        return groups;
    }

    Map<Long, Optional<ArtifactVersionMetaDataDto>> versions() {
        return versions;
    }

    Map<Long, Optional<List<ArtifactVersionMetaDataDto>>> contentUsers() {
        return contentUsers;
    }
}
