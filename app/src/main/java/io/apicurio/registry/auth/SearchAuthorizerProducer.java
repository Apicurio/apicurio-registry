package io.apicurio.registry.auth;

import java.util.Set;

import io.apicurio.registry.auth.opawasm.OpaWasmAccessController;
import io.apicurio.registry.auth.opawasm.OpaWasmAccessControllerConfig;
import io.apicurio.registry.auth.opawasm.OpaWasmSearchFilter;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SearchAuthorizerProducer {

    @Inject
    OpaWasmAccessControllerConfig opaConfig;

    @Inject
    OpaWasmSearchFilter opaFilter;

    @Inject
    OpaWasmAccessController opaAc;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Produces
    @ApplicationScoped
    public ISearchAuthorizer searchAuthorizer() {
        if (opaConfig.isEnabled()) {
            return new ISearchAuthorizer() {
                @Override
                public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit) {
                    return opaFilter.searchArtifacts(filters, orderBy, orderDir, offset, limit);
                }

                @Override
                public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit) {
                    return opaFilter.searchGroups(filters, orderBy, orderDir, offset, limit);
                }

                @Override
                public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
                        OrderDirection orderDir, int offset, int limit) {
                    return opaFilter.searchVersions(filters, orderBy, orderDir, offset, limit);
                }

                @Override
                public boolean canReadArtifact(String groupId, String artifactId) {
                    String user = securityIdentity != null && !securityIdentity.isAnonymous()
                            ? securityIdentity.getPrincipal().getName() : "anonymous";
                    Set<String> roles = securityIdentity != null ? securityIdentity.getRoles() : Set.of();
                    String resourceName = OpaWasmAccessController.buildResourceName(groupId, artifactId);
                    return opaAc.evaluate(user, roles, "read", "artifact", resourceName);
                }
            };
        }
        return new ISearchAuthorizer() {
            @Override
            public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit) {
                return storage.searchArtifacts(filters, orderBy, orderDir, offset, limit);
            }

            @Override
            public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit) {
                return storage.searchGroups(filters, orderBy, orderDir, offset, limit);
            }

            @Override
            public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit) {
                return storage.searchVersions(filters, orderBy, orderDir, offset, limit);
            }
        };
    }
}
