package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.ArtifactTypeToContentType;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.HandleReferencesType;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.utils.IoUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.rest.client.models.VersionState.DISABLED;

/**
 * An implementation of @{@link RegistryClientFacade} that uses version 3 of the
 * Apicurio Registry Core API.
 */
public class RegistryClientFacadeImpl implements RegistryClientFacade {

    private static final Logger LOG = LoggerFactory.getLogger(RegistryClientFacadeImpl.class);
    private static final String CLIENT_ID_HEADER = "X-Registry-Client-Id";
    private static final String OPERATION_HEADER = "X-Registry-Operation";

    private final RegistryClient client;
    private final String baseUrl;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final String clientId;

    public RegistryClientFacadeImpl(RegistryClient client) {
        this(client, null, null);
    }

    public RegistryClientFacadeImpl(RegistryClient client, String baseUrl, String clientId) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }


    @Override
    public RegistryClient getClient() {
        return client;
    }

    @Override
    public String getSchemaByContentId(Long contentId) {
        InputStream rawSchema = client.ids().contentIds().byContentId(contentId).get();
        return IoUtil.toString(rawSchema);
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentId(long contentId) {
        List<ArtifactReference> references = client.ids().contentIds().byContentId(contentId).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public String getSchemaByGlobalId(long globalId, boolean dereferenced) {
        InputStream rawSchema = client.ids().globalIds().byGlobalId(globalId).get(config -> {
            config.headers.add("CANONICAL", "false");
            if (clientId != null && !clientId.isEmpty()) {
                config.headers.add(CLIENT_ID_HEADER, clientId);
                String op = DefaultSchemaResolver.currentOperation.get();
                if (op != null) {
                    config.headers.add(OPERATION_HEADER, op);
                }
            }
            assert config.queryParameters != null;
            if (dereferenced) {
                config.queryParameters.references = HandleReferencesType.DEREFERENCE;
            }
        });
        return IoUtil.toString(rawSchema);
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGlobalId(long globalId) {
        List<ArtifactReference> references = client.ids().globalIds().byGlobalId(globalId).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public String getSchemaByGAV(String groupId, String artifactId, String version) {
        final InputStream rawSchema = client.groups()
                .byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).content().get();
        return IoUtil.toString(rawSchema);
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version) {
        List<ArtifactReference> references = client
                .groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public String getSchemaByContentHash(String contentHash) {
        InputStream rawSchema = client.ids().contentHashes().byContentHash(contentHash).get();
        return IoUtil.toString(rawSchema);
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentHash(String contentHash) {
        List<ArtifactReference> references = client.ids().contentHashes().byContentHash(contentHash).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public List<RegistryVersionCoordinates> searchVersionsByContent(String schemaString, String artifactType,
            io.apicurio.registry.resolver.strategy.ArtifactReference reference, boolean canonical) {

        InputStream is = new ByteArrayInputStream(schemaString.getBytes(StandardCharsets.UTF_8));
        String ct = ArtifactTypeToContentType.toContentType(artifactType);
        VersionSearchResults results = client.search().versions().post(is, ct, config -> {
            config.queryParameters.groupId = reference.getGroupId() == null ? "default"
                    : reference.getGroupId();
            config.queryParameters.artifactId = reference.getArtifactId();
            config.queryParameters.canonical = canonical;
            config.queryParameters.artifactType = artifactType;
            config.queryParameters.orderby = VersionSortBy.GlobalId;
            config.queryParameters.order = SortOrder.Desc;
            config.queryParameters.limit = 100;
        });

        // FIXME consider moving the "filter by state" logic to the server as another query parameter
        return results.getVersions().stream()
                .filter(version -> DISABLED != version.getState())
                .map(v ->
                RegistryVersionCoordinates.create(v.getGlobalId(), v.getContentId(), v.getGroupId(), v.getArtifactId(), v.getVersion())).toList();
    }

    @Override
    public RegistryVersionCoordinates createSchema(String artifactType, String groupId, String artifactId, String version,
                                    String autoCreateBehavior, boolean canonical, String schemaString,
                                    Set<RegistryArtifactReference> references) {
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        VersionContent content = new VersionContent();
        content.setContentType(ArtifactTypeToContentType.toContentType(artifactType));
        content.setContent(schemaString);
        content.setReferences(toClientReferences(references));
        createVersion.setContent(content);
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactType(artifactType);
        createArtifact.setArtifactId(artifactId);
        createArtifact.setFirstVersion(createVersion);

        IfArtifactExists ifExists = IfArtifactExists.forValue(autoCreateBehavior);
        CreateArtifactResponse response = client.groups()
                .byGroupId(groupId)
                .artifacts().post(createArtifact, config -> {
                    config.queryParameters.ifExists = ifExists;
                    config.queryParameters.canonical = canonical;
                });
        return RegistryVersionCoordinates.create(
                response.getVersion().getGlobalId(),
                response.getVersion().getContentId(),
                response.getVersion().getGroupId(),
                response.getVersion().getArtifactId(),
                response.getVersion().getVersion()
        );
    }

    @Override
    public RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version) {
        if (version == null) {
            version = "branch=latest";
        }

        VersionMetaData vmd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(version).get();
        return RegistryVersionCoordinates.create(vmd.getGlobalId(), vmd.getContentId(), vmd.getGroupId(), vmd.getArtifactId(), vmd.getVersion());
    }

    @Override
    public io.apicurio.registry.rest.client.models.ContractRuleSet getContractRuleset(
            String groupId, String artifactId) {
        try {
            String g = groupId != null ? groupId : "default";
            return client.groups().byGroupId(g).artifacts().byArtifactId(artifactId)
                    .contract().ruleset().get();
        } catch (Exception e) {
            LOG.warn("Failed to fetch contract ruleset for {}/{}: {}", groupId, artifactId, e.getMessage());
            return null;
        }
    }

    @Override
    public io.apicurio.registry.rest.client.models.ContractRuleSet getVersionContractRuleset(
            String groupId, String artifactId, String version) {
        try {
            String g = groupId != null ? groupId : "default";
            String ver = version != null ? version : "branch=latest";
            return client.groups().byGroupId(g).artifacts().byArtifactId(artifactId)
                    .versions().byVersionExpression(ver).contract().ruleset().get();
        } catch (Exception e) {
            LOG.warn("Failed to fetch version contract ruleset for {}/{}/{}: {}",
                    groupId, artifactId, version, e.getMessage());
            return null;
        }
    }

    private static List<ArtifactReference> toClientReferences(Set<RegistryArtifactReference> references) {
        return references.stream().map(ref -> {
            ArtifactReference ar = new ArtifactReference();
            ar.setName(ref.getName());
            ar.setGroupId(ref.getGroupId());
            ar.setArtifactId(ref.getArtifactId());
            ar.setVersion(ref.getVersion());
            return ar;
        }).toList();
    }

}
