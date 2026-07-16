package io.apicurio.registry.sync.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;

import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;

import io.apicurio.registry.sync.model.ApicurioAgentCardSpec;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ApicurioAgentCardClient {

    private static final Logger log = LoggerFactory.getLogger(ApicurioAgentCardClient.class);

    @ConfigProperty(name = "apicurio.registry.url", defaultValue = "http://localhost:8080/apis/registry/v3")
    public String registryUrl;

    @ConfigProperty(name = "apicurio.registry.auth.username")
    Optional<String> username;

    @ConfigProperty(name = "apicurio.registry.auth.password")
    Optional<String> password;

    @Inject
    public Vertx vertx;

    @Inject
    public ObjectMapper objectMapper;

    private RegistryClient client;

    // Cache to prevent unnecessary Kubernetes updates
    // Map of artifactId -> CacheEntry containing globalId, contentId, and modifiedOn timestamp
    private final Map<String, CacheEntry> syncCache = new ConcurrentHashMap<>();

    public synchronized RegistryClient getClient() {
        if (client == null) {
            log.info("Initializing Apicurio Registry client targeting: {}", registryUrl);
            RegistryClientOptions options = RegistryClientOptions.create()
                    .registryUrl(registryUrl)
                    .vertx(vertx);
            if (username != null && username.isPresent() && !username.get().trim().isEmpty()) {
                options.basicAuth(username.get(), password != null ? password.orElse("") : "");
            }


            client = RegistryClientFactory.create(options);
        }
        return client;
    }

    public List<AgentCardVersionInfo> getAgentCardVersions() {
        try {
            log.debug("Polling Apicurio Registry for AGENT_CARD artifacts");
            VersionSearchResults results = getClient().search().versions().get(config -> {
                config.queryParameters.artifactType = "AGENT_CARD";
            });

            if (results == null || results.getVersions() == null) {
                return Collections.emptyList();
            }

            List<AgentCardVersionInfo> list = new ArrayList<>();
            for (SearchedVersion version : results.getVersions()) {
                list.add(new AgentCardVersionInfo(
                        version.getGroupId(),
                        version.getArtifactId(),
                        version.getVersion(),
                        version.getGlobalId(),
                        version.getContentId(),
                        version.getModifiedOn() != null ? version.getModifiedOn().toInstant().toEpochMilli() : 0L

                ));
            }
            return list;
        } catch (Exception e) {
            log.error("Failed to query AGENT_CARD versions from Apicurio Registry", e);
            throw new RuntimeException("Failed to query AGENT_CARD versions from Apicurio Registry", e);
        }
    }

    public ApicurioAgentCardSpec fetchAgentCardSpec(String groupId, String artifactId, String versionExpression) {
        try {
            log.debug("Fetching content for agent card artifactId: {}, version: {}", artifactId, versionExpression);
            String resolvedGroupId = (groupId == null || groupId.trim().isEmpty()) ? "default" : groupId;
            try (InputStream is = getClient().groups().byGroupId(resolvedGroupId).artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression(versionExpression).content().get()) {
                return objectMapper.readValue(is, ApicurioAgentCardSpec.class);
            }
        } catch (Exception e) {
            log.error("Failed to fetch agent card spec for artifact: {}", artifactId, e);
            return null;
        }
    }

    public boolean isChanged(String artifactId, long globalId, long contentId, long modifiedOn) {
        CacheEntry cached = syncCache.get(artifactId);
        if (cached == null || cached.globalId != globalId || cached.contentId != contentId || cached.modifiedOn != modifiedOn) {
            return true;
        }
        return false;
    }

    public void updateCache(String artifactId, long globalId, long contentId, long modifiedOn) {
        syncCache.put(artifactId, new CacheEntry(globalId, contentId, modifiedOn));
    }

    public void evictCache(String artifactId) {
        syncCache.remove(artifactId);
    }

    private static class CacheEntry {
        final long globalId;
        final long contentId;
        final long modifiedOn;

        CacheEntry(long globalId, long contentId, long modifiedOn) {
            this.globalId = globalId;
            this.contentId = contentId;
            this.modifiedOn = modifiedOn;
        }
    }

    public static class AgentCardVersionInfo {
        public final String groupId;
        public final String artifactId;
        public final String version;
        public final long globalId;
        public final long contentId;
        public final long modifiedOn;

        public AgentCardVersionInfo(String groupId, String artifactId, String version, long globalId, long contentId, long modifiedOn) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.globalId = globalId;
            this.contentId = contentId;
            this.modifiedOn = modifiedOn;
        }
    }
}
