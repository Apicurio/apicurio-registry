package io.apicurio.registry.examples.util;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.rest.client.util.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class RegistryDemoUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryDemoUtil.class);

    /**
     * Create the artifact in the registry (or update it if it already exists).
     *
     * @param artifactId
     * @param schema
     */
    public static void createSchemaInServiceRegistry(RegistryClient service, String artifactId, String schema) {

        LOGGER.info("---------------------------------------------------------");
        LOGGER.info("=====> Creating artifact in the registry for JSON Schema with ID: {}", artifactId);
        try {
            final ByteArrayInputStream content = new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setArtifactType("JSON");
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(content));
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            final io.apicurio.registry.rest.client.models.VersionMetaData metaData = service.groups().byGroupId("default").artifacts().post(createArtifact, config -> {
                config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
            }).getVersion();

            assert metaData != null;
            LOGGER.info("=====> Successfully created JSON Schema artifact in Service Registry: {}", metaData);
            LOGGER.info("---------------------------------------------------------");
        } catch (Exception t) {
            throw t;
        }
    }

    /**
     * Get the artifact from the registry.
     *
     * @param artifactId
     */
    public static ArtifactMetaData getSchemaFromRegistry(RegistryClient service, String artifactId) {

        LOGGER.info("---------------------------------------------------------");
        LOGGER.info("=====> Fetching artifact from the registry for JSON Schema with ID: {}", artifactId);
        try {
            final ArtifactMetaData metaData = service.groups().byGroupId("default").artifacts().byArtifactId(artifactId).get();
            assert metaData != null;
            LOGGER.info("=====> Successfully fetched JSON Schema artifact in Service Registry: {}", metaData);
            LOGGER.info("---------------------------------------------------------");
            return metaData;
        } catch (Exception t) {
            throw t;
        }
    }

    /**
     * Delete the artifact from the registry.
     *
     * @param artifactId
     */
    public static void deleteSchema(RegistryClient service, String artifactId) {

        LOGGER.info("---------------------------------------------------------");
        LOGGER.info("=====> Deleting artifact from the registry for JSON Schema with ID: {}", artifactId);
        try {
            service.groups().byGroupId("default").artifacts().byArtifactId(artifactId).delete();
            LOGGER.info("=====> Successfully deleted JSON Schema artifact in Service Registry.");
            LOGGER.info("---------------------------------------------------------");
        } catch (Exception t) {
            throw t;
        }
    }
}
