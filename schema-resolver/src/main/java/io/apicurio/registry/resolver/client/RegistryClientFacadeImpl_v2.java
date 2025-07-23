package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.v2.RegistryClient;
import io.apicurio.registry.rest.client.v2.models.ArtifactContent;
import io.apicurio.registry.rest.client.v2.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.v2.models.IfExists;
import io.apicurio.registry.rest.client.v2.models.VersionMetaData;
import io.apicurio.registry.utils.IoUtil;
import io.vertx.core.Vertx;

import java.io.InputStream;
import java.util.List;

/**
 * An implementation of @{@link RegistryClientFacade} that uses version 2 of the
 * Apicurio Registry Core API.
 */
public class RegistryClientFacadeImpl_v2 implements RegistryClientFacade {

    private final RegistryClient client;
    private Vertx vertx;

    public RegistryClientFacadeImpl_v2(RegistryClient client) {
        this(client, null);
    }

    public RegistryClientFacadeImpl_v2(RegistryClient client, Vertx vertx) {
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
    public String getSchemaByGlobalId(long globalId, boolean dereferenced) {
        InputStream rawSchema = client.ids().globalIds().byGlobalId(globalId).get(config -> {
            assert config.queryParameters != null;
            if (dereferenced) {
                config.queryParameters.dereference = true;
            }
        });
        return IoUtil.toString(rawSchema);
    }

    @Override
    public String getSchemaByGAV(String groupId, String artifactId, String version) {
        final InputStream rawSchema = client.groups()
                .byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).versions()
                .byVersion(version).get();
        return IoUtil.toString(rawSchema);
    }

    @Override
    public String getSchemaByContentHash(String contentHash) {
        InputStream rawSchema = client.ids().contentHashes().byContentHash(contentHash).get();
        return IoUtil.toString(rawSchema);
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentId(long contentId) {
        List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> references = client.ids().contentIds()
                .byContentId(contentId).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGlobalId(long globalId) {
        List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> references = client.ids().globalIds()
                .byGlobalId(globalId).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version) {
        List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> references = client
                .groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).versions()
                .byVersion(version).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public List<RegistryArtifactReference> getReferencesByContentHash(String contentHash) {
        List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> references = client.ids().contentHashes()
                .byContentHash(contentHash).references().get();
        return references.stream().map(RegistryArtifactReference::fromClientArtifactReference).toList();
    }

    @Override
    public List<RegistryVersionCoordinates> searchVersionsByContent(String schemaString, String artifactType,
                                                                    ArtifactReference reference, boolean canonical) {
        ArtifactContent content = new ArtifactContent();
        content.setContent(schemaString);
        String groupId = reference.getGroupId() == null ? "default" : reference.getGroupId();
        String artifactId = reference.getArtifactId();

        VersionMetaData vmd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).meta()
                .post(content, config -> {
                    config.queryParameters.canonical = canonical;
                });

        return List.of(RegistryVersionCoordinates.create(
                vmd.getGlobalId(), vmd.getContentId(), vmd.getGroupId(), vmd.getId(), vmd.getVersion()));
    }

    @Override
    public RegistryVersionCoordinates createSchema(String artifactType, String groupId, String artifactId, String version,
                                                   String autoCreateBehavior, boolean canonical, String schemaString,
                                                   List<RegistryArtifactReference> references) {
        ArtifactContent content = new ArtifactContent();
        content.setContent(schemaString);
        content.setReferences(toClientReferences(references));
        IfArtifactExists ifArtifactExists = IfArtifactExists.forValue(autoCreateBehavior);
        IfExists ifExists;
        switch (ifArtifactExists) {
            case FIND_OR_CREATE_VERSION -> ifExists = IfExists.RETURN_OR_UPDATE;
            case FAIL -> ifExists = IfExists.FAIL;
            default -> ifExists = IfExists.UPDATE;
        }

        ArtifactMetaData amd = client.groups().byGroupId(groupId)
                .artifacts().post(content, config -> {
                    config.queryParameters.ifExists = ifExists;
                    config.queryParameters.canonical = canonical;
                    if (version != null) {
                        config.headers.add("X-Registry-Version", version);
                    }
                    if (artifactId != null) {
                        config.headers.add("X-Registry-ArtifactId", artifactId);
                    }
                    if (artifactType != null) {
                        config.headers.add("X-Registry-ArtifactType", artifactType);
                    }
                });

        return RegistryVersionCoordinates.create(amd.getGlobalId(), amd.getContentId(), amd.getGroupId(),
                amd.getId(), amd.getVersion());
    }

    @Override
    public RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version) {
        if (version == null) {
            version = "latest";
        }

        VersionMetaData vmd = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().byVersion(version).meta().get();
        return RegistryVersionCoordinates.create(vmd.getGlobalId(), vmd.getContentId(), vmd.getGroupId(), vmd.getId(), vmd.getVersion());
    }

    @Override
    public void close() throws Exception {
        if (vertx != null) {
            vertx.close();
            vertx = null;
        }
    }

    private static List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> toClientReferences(List<RegistryArtifactReference> references) {
        return references.stream().map(ref -> {
            io.apicurio.registry.rest.client.v2.models.ArtifactReference ar = new io.apicurio.registry.rest.client.v2.models.ArtifactReference();
            ar.setName(ref.getName());
            ar.setGroupId(ref.getGroupId());
            ar.setArtifactId(ref.getArtifactId());
            ar.setVersion(ref.getVersion());
            return ar;
        }).toList();
    }
}
