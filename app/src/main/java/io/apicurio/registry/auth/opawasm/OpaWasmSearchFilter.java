package io.apicurio.registry.auth.opawasm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedGroupDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

@ApplicationScoped
public class OpaWasmSearchFilter {

    private static final int FETCH_MULTIPLIER = 3;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    OpaWasmAccessController opaWasmAc;

    @Inject
    OpaWasmAccessControllerConfig config;

    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        if (!config.isEnabled() || opaWasmAc.getPolicyPool() == null) {
            return storage.searchArtifacts(filters, orderBy, orderDir, offset, limit);
        }

        String user = getUsername();
        Set<String> roles = getRoles();

        int fetched = 0;
        int skipped = 0;
        List<SearchedArtifactDto> collected = new ArrayList<>();

        while (collected.size() < limit) {
            int fetchSize = limit * FETCH_MULTIPLIER;
            ArtifactSearchResultsDto batch = storage.searchArtifacts(filters, orderBy, orderDir,
                    fetched, fetchSize);

            if (batch.getArtifacts().isEmpty()) {
                break;
            }

            for (SearchedArtifactDto artifact : batch.getArtifacts()) {
                fetched++;
                String resourceName = (artifact.getGroupId() != null ? artifact.getGroupId() : "default")
                        + "/" + artifact.getArtifactId();
                if (opaWasmAc.evaluate(user, roles, "read", "artifact", resourceName)) {
                    if (skipped < offset) {
                        skipped++;
                    } else {
                        collected.add(artifact);
                        if (collected.size() >= limit) {
                            break;
                        }
                    }
                }
            }

            if (batch.getArtifacts().size() < fetchSize) {
                break;
            }
        }

        ArtifactSearchResultsDto result = new ArtifactSearchResultsDto();
        result.setArtifacts(collected);
        result.setCount((long) collected.size());
        return result;
    }

    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        if (!config.isEnabled() || opaWasmAc.getPolicyPool() == null) {
            return storage.searchGroups(filters, orderBy, orderDir, offset, limit);
        }

        String user = getUsername();
        Set<String> roles = getRoles();

        int fetched = 0;
        int skipped = 0;
        List<SearchedGroupDto> collected = new ArrayList<>();

        while (collected.size() < limit) {
            int fetchSize = limit * FETCH_MULTIPLIER;
            GroupSearchResultsDto batch = storage.searchGroups(filters, orderBy, orderDir,
                    fetched, fetchSize);

            if (batch.getGroups().isEmpty()) {
                break;
            }

            for (SearchedGroupDto group : batch.getGroups()) {
                fetched++;
                if (opaWasmAc.evaluate(user, roles, "read", "group", group.getId())) {
                    if (skipped < offset) {
                        skipped++;
                    } else {
                        collected.add(group);
                        if (collected.size() >= limit) {
                            break;
                        }
                    }
                }
            }

            if (batch.getGroups().size() < fetchSize) {
                break;
            }
        }

        GroupSearchResultsDto result = new GroupSearchResultsDto();
        result.setGroups(collected);
        result.setCount(collected.size());
        return result;
    }

    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDir, int offset, int limit) {
        if (!config.isEnabled() || opaWasmAc.getPolicyPool() == null) {
            return storage.searchVersions(filters, orderBy, orderDir, offset, limit);
        }

        String user = getUsername();
        Set<String> roles = getRoles();

        int fetched = 0;
        int skipped = 0;
        List<SearchedVersionDto> collected = new ArrayList<>();

        while (collected.size() < limit) {
            int fetchSize = limit * FETCH_MULTIPLIER;
            VersionSearchResultsDto batch = storage.searchVersions(filters, orderBy, orderDir,
                    fetched, fetchSize);

            if (batch.getVersions().isEmpty()) {
                break;
            }

            for (SearchedVersionDto version : batch.getVersions()) {
                fetched++;
                String resourceName = (version.getGroupId() != null ? version.getGroupId() : "default")
                        + "/" + version.getArtifactId();
                if (opaWasmAc.evaluate(user, roles, "read", "artifact", resourceName)) {
                    if (skipped < offset) {
                        skipped++;
                    } else {
                        collected.add(version);
                        if (collected.size() >= limit) {
                            break;
                        }
                    }
                }
            }

            if (batch.getVersions().size() < fetchSize) {
                break;
            }
        }

        VersionSearchResultsDto result = new VersionSearchResultsDto();
        result.setVersions(collected);
        result.setCount((long) collected.size());
        return result;
    }

    private String getUsername() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        return "anonymous";
    }

    private Set<String> getRoles() {
        if (securityIdentity != null) {
            return securityIdentity.getRoles();
        }
        return Set.of();
    }
}
