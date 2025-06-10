package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Logger;

import static io.apicurio.registry.rest.client.models.VersionState.DISABLED;

public class SchemaByContentResolverClient {
    private static final Logger logger = Logger.getLogger(SchemaByContentResolverClient.class.getSimpleName());

    private final RegistryClient client;

    public SchemaByContentResolverClient(RegistryClient client) {
        this.client = client;
    }

    public SearchedVersion handleResolveSchemaByContent(String rawSchemaString,
                                                        ArtifactReference artifactReference,
                                                        String artifactType,
                                                        String contentType) {

        logger.info(String.format("Retrieving schema content using string: %s", rawSchemaString));

        InputStream is = new ByteArrayInputStream(rawSchemaString.getBytes(StandardCharsets.UTF_8));
        VersionSearchResults results = client.search().versions().post(is, contentType, config -> {
            config.queryParameters.groupId = artifactReference.getGroupId() == null ? "default"
                    : artifactReference.getGroupId();
            config.queryParameters.artifactId = artifactReference.getArtifactId();
            config.queryParameters.canonical = true;
            config.queryParameters.artifactType = artifactType;
            config.queryParameters.orderby = VersionSortBy.GlobalId;
            config.queryParameters.order = SortOrder.Desc;
        });

        if (results.getCount() == 0) {
            is = new ByteArrayInputStream(rawSchemaString.getBytes(StandardCharsets.UTF_8));
            results = client.search().versions().post(is, contentType, config -> {
                config.queryParameters.groupId = artifactReference.getGroupId() == null ? "default"
                        : artifactReference.getGroupId();
                config.queryParameters.artifactId = artifactReference.getArtifactId();
                config.queryParameters.canonical = false;
                config.queryParameters.artifactType = artifactType;
                config.queryParameters.orderby = VersionSortBy.GlobalId;
                config.queryParameters.order = SortOrder.Desc;
            });
        }

        SearchedVersion searchedVersion = Optional.ofNullable(results.getVersions())
                .flatMap(versions -> versions.stream()
                        .filter(version -> version.getState() != DISABLED)
                        .findFirst())
                .orElseThrow(() -> new RuntimeException(
                        "Could not resolve artifact reference by content: %s&%s".formatted(rawSchemaString, artifactReference)));


        return searchedVersion;
    }
}
