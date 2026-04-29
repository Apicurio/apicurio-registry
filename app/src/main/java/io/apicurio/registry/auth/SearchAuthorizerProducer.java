package io.apicurio.registry.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.registry.auth.opawasm.OpaWasmAccessController;
import io.apicurio.registry.auth.opawasm.RegistryResourceType;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Decision;
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
                    return opaAc.canReadArtifact(groupId, artifactId);
                }

                @Override
                public List<String> getArtifactPermissions(String groupId, String artifactId) {
                    GrantsAuthorizer auth = opaAc.getAuthorizer();
                    if (auth == null) {
                        return List.of();
                    }
                    String resourceName = OpaWasmAccessController.buildResourceName(groupId, artifactId);
                    io.kroxylicious.proxy.authentication.Subject subject = buildSubject();
                    AuthorizeResult result = auth.authorize(subject, List.of(
                            new Action(RegistryResourceType.Artifact.Read, resourceName),
                            new Action(RegistryResourceType.Artifact.Write, resourceName),
                            new Action(RegistryResourceType.Artifact.Admin, resourceName)))
                            .toCompletableFuture().join();
                    List<String> perms = new ArrayList<>();
                    if (result.decision(RegistryResourceType.Artifact.Read, resourceName) == Decision.ALLOW) {
                        perms.add("read");
                    }
                    if (result.decision(RegistryResourceType.Artifact.Write, resourceName) == Decision.ALLOW) {
                        perms.add("write");
                    }
                    if (result.decision(RegistryResourceType.Artifact.Admin, resourceName) == Decision.ALLOW) {
                        perms.add("admin");
                    }
                    return perms;
                }

                @Override
                public List<String> getGroupPermissions(String groupId) {
                    GrantsAuthorizer auth = opaAc.getAuthorizer();
                    if (auth == null) {
                        return List.of();
                    }
                    io.kroxylicious.proxy.authentication.Subject subject = buildSubject();
                    AuthorizeResult result = auth.authorize(subject, List.of(
                            new Action(RegistryResourceType.Group.Read, groupId),
                            new Action(RegistryResourceType.Group.Write, groupId),
                            new Action(RegistryResourceType.Group.Admin, groupId)))
                            .toCompletableFuture().join();
                    List<String> perms = new ArrayList<>();
                    if (result.decision(RegistryResourceType.Group.Read, groupId) == Decision.ALLOW) {
                        perms.add("read");
                    }
                    if (result.decision(RegistryResourceType.Group.Write, groupId) == Decision.ALLOW) {
                        perms.add("write");
                    }
                    if (result.decision(RegistryResourceType.Group.Admin, groupId) == Decision.ALLOW) {
                        perms.add("admin");
                    }
                    return perms;
                }

                private io.kroxylicious.proxy.authentication.Subject buildSubject() {
                    if (securityIdentity == null || securityIdentity.isAnonymous()) {
                        return io.kroxylicious.proxy.authentication.Subject.anonymous();
                    }
                    var principals = new java.util.HashSet<io.kroxylicious.proxy.authentication.Principal>();
                    principals.add(new io.kroxylicious.proxy.authentication.User(
                            securityIdentity.getPrincipal().getName()));
                    for (String role : securityIdentity.getRoles()) {
                        principals.add(new io.apicurio.authz.RolePrincipal(role));
                    }
                    return new io.kroxylicious.proxy.authentication.Subject(principals);
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
