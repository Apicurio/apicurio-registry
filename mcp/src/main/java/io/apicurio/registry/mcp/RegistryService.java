package io.apicurio.registry.mcp;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.rest.client.models.UpdateConfigurationProperty;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ApplicationScoped
public class RegistryService {

    private static final Logger log = LoggerFactory.getLogger(RegistryService.class);

    @ConfigProperty(name = "registry.url", defaultValue = "localhost:8080")
    String rawBaseUrl;

    @Inject
    McpConfig config;

    @Inject
    Utils utils;

    private RegistryClient client;

    @PostConstruct
    void init() {
        var options = RegistryClientOptions.create(rawBaseUrl).retry();
        configureAuthentication(options);
        configureTls(options);
        client = RegistryClientFactory.create(options);
        // Test the connection
        var info = client.system().info().get();
        log.info("Successfully connected to Apicurio Registry version {} at {}", info.getVersion(), rawBaseUrl);
    }

    private void configureAuthentication(RegistryClientOptions options) {
        var auth = config.auth();
        if (!auth.enabled()) {
            log.info("Authentication is disabled");
            return;
        }

        if (auth.tokenEndpoint().isEmpty() || auth.clientId().isEmpty() || auth.clientSecret().isEmpty()) {
            throw new IllegalStateException(
                    "OAuth2 authentication requires 'apicurio.mcp.auth.token-endpoint', "
                            + "'apicurio.mcp.auth.client-id', and 'apicurio.mcp.auth.client-secret' to be configured");
        }

        if (auth.scope().isPresent()) {
            options.oauth2(auth.tokenEndpoint().get(), auth.clientId().get(), auth.clientSecret().get(),
                    auth.scope().get());
        } else {
            options.oauth2(auth.tokenEndpoint().get(), auth.clientId().get(), auth.clientSecret().get());
        }
        log.info("Configured OAuth2 authentication with token endpoint: {}", auth.tokenEndpoint().get());
    }

    private void configureTls(RegistryClientOptions options) {
        var tls = config.tls();

        // Trust all certificates (development only)
        if (tls.trustAll()) {
            log.warn("TLS trust-all is enabled. This should only be used in development environments.");
            options.trustAll(true);
        }

        // Hostname verification
        if (!tls.verifyHost()) {
            log.warn("TLS hostname verification is disabled. This reduces security.");
            options.verifyHost(false);
        }

        // Trust store configuration
        var truststore = tls.truststore();
        if (truststore.type().isPresent() && truststore.path().isPresent()) {
            String type = truststore.type().get().toUpperCase();
            String path = truststore.path().get();
            String password = truststore.password().orElse(null);

            switch (type) {
                case "JKS":
                    options.trustStoreJks(path, password);
                    log.info("Configured JKS trust store: {}", path);
                    break;
                case "PKCS12":
                case "P12":
                    options.trustStorePkcs12(path, password);
                    log.info("Configured PKCS12 trust store: {}", path);
                    break;
                case "PEM":
                    options.trustStorePem(path);
                    log.info("Configured PEM trust store: {}", path);
                    break;
                default:
                    throw new IllegalStateException("Unsupported trust store type: " + type
                            + ". Supported types: JKS, PKCS12, PEM");
            }
        }

        // Key store configuration (mTLS)
        var keystore = tls.keystore();
        if (keystore.type().isPresent() && keystore.path().isPresent()) {
            String type = keystore.type().get().toUpperCase();
            String path = keystore.path().get();
            String password = keystore.password().orElse(null);

            switch (type) {
                case "JKS":
                    options.keystoreJks(path, password);
                    log.info("Configured JKS key store for mTLS: {}", path);
                    break;
                case "PKCS12":
                case "P12":
                    options.keystorePkcs12(path, password);
                    log.info("Configured PKCS12 key store for mTLS: {}", path);
                    break;
                default:
                    throw new IllegalStateException("Unsupported key store type: " + type
                            + ". Supported types: JKS, PKCS12");
            }
        }
    }

    public SystemInfo getServerInfo() {
        return client.system().info().get();
    }

    public List<SearchedGroup> listGroups(
            String order,
            String groupOrderBy
    ) {
        var page = client.groups().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = GroupSortBy.forValue(groupOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getGroups();
    }

    private void checkPagingLimit(int count) {
        if (config.paging().limitError() && count > config.paging().limit()) {
            throw new ToolCallException("""
                    Apicurio Registry contains more than %s objects, which is the currently configured paging limit. \
                    Use configuration properties "apicurio.mcp.paging.limit" and "apicurio.mcp.paging.limit-error" to configure how paging is handled."""
                    .formatted(config.paging().limit()));
        }
    }

    public GroupMetaData createGroup(
            String groupId,
            String description,
            String jsonLabels
    ) {
        var g = new CreateGroup();
        g.setGroupId(groupId);
        g.setDescription(description);
        g.setLabels(utils.toLabels(jsonLabels));

        return client.groups().post(g);
    }

    public GroupMetaData getGroupMetadata(
            String groupId
    ) {
        return client.groups().byGroupId(groupId).get();
    }

    public void updateGroupMetadata(
            String groupId,
            String description,
            String jsonLabels
    ) {
        var m = new EditableGroupMetaData();
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client.groups().byGroupId(groupId).put(m);
    }

    public List<ArtifactTypeInfo> getArtifactTypes() {
        return client.admin().config().artifactTypes().get();
    }

    public List<SearchedArtifact> listArtifacts(
            String groupId,
            String order,
            String artifactOrderBy
    ) {
        var page = client.groups().byGroupId(groupId).artifacts().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = ArtifactSortBy.forValue(artifactOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getArtifacts();
    }

    public ArtifactMetaData getArtifactMetadata(
            String groupId,
            String artifactId
    ) {
        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
    }

    public void updateArtifactMetadata(
            String groupId,
            String artifactId,
            String name,
            String description,
            String jsonLabels
    ) {
        var m = new EditableArtifactMetaData();
        m.setName(name);
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(m);
    }

    public void updateVersionMetadata(
            String groupId,
            String artifactId,
            String versionExpression,
            String name,
            String description,
            String jsonLabels
    ) {
        var m = new EditableVersionMetaData();
        m.setName(name);
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression).put(m);
    }

    public String getVersionContent(
            String groupId,
            String artifactId,
            String versionExpression
    ) throws IOException {
        return new String(client
                .groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .content().get().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    public VersionMetaData getVersionMetadata(
            String groupId,
            String artifactId,
            String versionExpression
    ) {
        return client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .get();
    }

    public void updateVersionContent(
            String groupId,
            String artifactId,
            String versionExpression,
            String versionContentType,
            String versionContent
    ) {
        var vc = new VersionContent();
        vc.setContentType(versionContentType);
        vc.setContent(versionContent);

        client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .content().put(vc);
    }

    public List<SearchedVersion> listVersions(
            String groupId,
            String artifactId,
            String order,
            String versionOrderBy
    ) {
        var page = client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions()
                .get(r -> {
                    r.queryParameters.limit = config.paging().limit() + 1;
                    r.queryParameters.order = SortOrder.forValue(order);
                    r.queryParameters.orderby = VersionSortBy.forValue(versionOrderBy);
                });
        checkPagingLimit(page.getCount());
        return page.getVersions();
    }

    public ArtifactMetaData createArtifact(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels
    ) {
        var a = new CreateArtifact();
        a.setArtifactId(artifactId);
        a.setArtifactType(artifactType);
        a.setName(name);
        a.setDescription(description);
        a.setLabels(utils.toLabels(jsonLabels));

        return client.groups().byGroupId(groupId).artifacts().post(a).getArtifact();
    }

    public VersionMetaData createVersion(
            String groupId,
            String artifactId,
            String version,
            String versionContentType,
            String versionContent,
            String name,
            String description,
            String jsonLabels,
            Boolean isDraft
    ) {
        var v = new CreateVersion();
        v.setVersion(version);
        v.setName(name);
        v.setDescription(description);
        v.setLabels(utils.toLabels(jsonLabels));
        v.setIsDraft(isDraft);

        var c = new VersionContent();
        c.setContentType(versionContentType);
        c.setContent(versionContent);
        v.setContent(c);

        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(v);
    }

    public void updateVersionState(
            String groupId,
            String artifactId,
            String versionExpression,
            String versionState
    ) {
        var vs = new WrappedVersionState();
        vs.setState(VersionState.valueOf(versionState));

        client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .state().put(vs);
    }

    public List<SearchedGroup> searchGroups(
            String groupId,
            String description,
            String labels,
            String order,
            String groupOrderBy
    ) {
        var page = client.search().groups().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(labels);

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = GroupSortBy.forValue(groupOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getGroups();
    }

    public List<SearchedVersion> searchVersions(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String versionOrderBy
    ) {
        var page = client.search().versions().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.artifactId = artifactId;
            r.queryParameters.artifactType = artifactType;
            r.queryParameters.name = name;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(jsonLabels);

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = VersionSortBy.forValue(versionOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getVersions();
    }

    public List<SearchedArtifact> searchArtifacts(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String artifactOrderBy
    ) {
        var page = client.search().artifacts().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.artifactId = artifactId;
            r.queryParameters.artifactType = artifactType;
            r.queryParameters.name = name;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(jsonLabels);

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = ArtifactSortBy.forValue(artifactOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getArtifacts();
    }

    public List<ConfigurationProperty> listConfigurationProperties() {
        return client.admin().config().properties().get();
    }

    public ConfigurationProperty getConfigurationProperty(String propertyName) {
        return client.admin().config().properties().byPropertyName(propertyName).get();
    }

    public void updateConfigurationProperty(String propertyName, String propertyValue) {
        if (config.safeMode() && !List.of(
                "apicurio.rest.mutability.artifact-version-content.enabled"
        ).contains(propertyName)) {
            throw new ToolCallException("Configuration property can't be updated because it's not in the whitelist.");
        }
        var p = new UpdateConfigurationProperty();
        p.setValue(propertyValue);
        client.admin().config().properties().byPropertyName(propertyName).put(p);
    }
}
