package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.ArtifactTypeToContentType;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.*;
import io.apicurio.registry.utils.IoUtil;
import io.vertx.core.Vertx;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.apicurio.registry.rest.client.models.VersionState.DISABLED;

/**
 * An implementation of @{@link RegistryClientFacade} that uses version 3 of the
 * Apicurio Registry Core API.
 */
public class RegistryClientFacadeImpl implements RegistryClientFacade {

    private final RegistryClient client;
    private Vertx vertx;

    public RegistryClientFacadeImpl(RegistryClient client) {
        this(client, null);
    }

    public RegistryClientFacadeImpl(RegistryClient client, Vertx vertx) {
        this.client = client;
        this.vertx = vertx;
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
        });

        return results.getVersions().stream()
                .filter(version -> DISABLED != version.getState())
                .map(v ->
                RegistryVersionCoordinates.create(v.getGlobalId(), v.getContentId(), v.getGroupId(), v.getArtifactId(), v.getVersion())).toList();
    }

    @Override
    public RegistryVersionCoordinates createSchema(String artifactType, String groupId, String artifactId, String version,
                                                   String autoCreateBehavior, boolean canonical, String schemaString, List<RegistryArtifactReference> references) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(artifactType);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        createArtifact.setFirstVersion(createVersion);

        VersionContent versionContent = new VersionContent();
        versionContent.setContent(schemaString);
        versionContent.setContentType(ArtifactTypeToContentType.toContentType(artifactType));
        if (references != null && !references.isEmpty()) {
            versionContent.setReferences(toClientReferences(references));
        }
        createVersion.setContent(versionContent);

        CreateArtifactResponse car = client.groups().byGroupId(groupId)
                .artifacts().post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.forValue(autoCreateBehavior);
                    config.queryParameters.canonical = canonical;
                });

        return RegistryVersionCoordinates.create(car.getVersion().getGlobalId(), car.getVersion().getContentId(),
                car.getVersion().getGroupId(), car.getVersion().getArtifactId(), car.getVersion().getVersion());
    }

    @Override
    public RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version) {
        if (version == null) {
            version = "branch=latest";
        }

        VersionMetaData vmd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersionExpression(version).get();
        return RegistryVersionCoordinates.create(vmd.getGlobalId(), vmd.getContentId(), vmd.getGroupId(), vmd.getArtifactId(), vmd.getVersion());
    }

    private static List<ArtifactReference> toClientReferences(List<RegistryArtifactReference> references) {
        return references.stream().map(ref -> {
            ArtifactReference ar = new ArtifactReference();
            ar.setName(ref.getName());
            ar.setGroupId(ref.getGroupId());
            ar.setArtifactId(ref.getArtifactId());
            ar.setVersion(ref.getVersion());
            return ar;
        }).toList();
    }

    @Override
    public void close() throws Exception {
        if (vertx != null) {
            vertx.close();
            vertx = null;
        }
    }

}
