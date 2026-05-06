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
import io.apicurio.registry.resolver.telemetry.UsageTelemetryEvent;
import io.apicurio.registry.utils.IoUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.apicurio.registry.rest.client.models.VersionState.DISABLED;

/**
 * An implementation of @{@link RegistryClientFacade} that uses version 3 of the
 * Apicurio Registry Core API.
 */
public class RegistryClientFacadeImpl implements RegistryClientFacade {

    private static final Logger logger = Logger.getLogger(RegistryClientFacadeImpl.class.getName());
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final int HTTP_CONFLICT = 409;

    private final RegistryClient client;
    private final String baseUrl;
    private volatile boolean telemetryDisabledOnServer = false;

    public RegistryClientFacadeImpl(RegistryClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public RegistryClientFacadeImpl(RegistryClient client) {
        this(client, null);
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
                                                   String autoCreateBehavior, boolean canonical, String schemaString, Set<RegistryArtifactReference> references) {
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

    @Override
    public void reportUsageEvents(List<UsageTelemetryEvent> events) {
        if (baseUrl == null || telemetryDisabledOnServer) {
            return;
        }
        try {
            StringBuilder json = new StringBuilder("{\"events\":[");
            for (int i = 0; i < events.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                UsageTelemetryEvent e = events.get(i);
                json.append("{\"clientId\":\"").append(escapeJson(e.getClientId())).append("\"");
                json.append(",\"groupId\":\"").append(escapeJson(e.getGroupId())).append("\"");
                json.append(",\"artifactId\":\"").append(escapeJson(e.getArtifactId())).append("\"");
                json.append(",\"version\":\"").append(escapeJson(e.getVersion())).append("\"");
                json.append(",\"globalId\":").append(e.getGlobalId());
                json.append(",\"operation\":\"").append(e.getOperation()).append("\"");
                json.append(",\"timestamp\":").append(e.getTimestamp());
                json.append("}");
            }
            json.append("]}");

            String url = baseUrl.endsWith("/") ? baseUrl + "admin/usage/events"
                    : baseUrl + "/admin/usage/events";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == HTTP_CONFLICT) {
                logger.info("Usage telemetry is not enabled on the registry server. Disabling client reporting.");
                telemetryDisabledOnServer = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while reporting usage telemetry events", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to report usage telemetry events to registry", e);
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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
