package io.apicurio.registry.flink.state;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for registering and tracking Flink state schemas.
 *
 * <p>
 * State schemas are stored as artifacts in a dedicated group with the
 * suffix "-state" appended to the original group name. Each operator's
 * state schema is stored as a separate artifact.
 */
public final class StateSchemaService {

    /** Default suffix for state schema groups. */
    public static final String DEFAULT_STATE_GROUP_SUFFIX = "-state";

    /** The registry client. */
    private final RegistryClient client;

    /** The base group ID. */
    private final String baseGroupId;

    /** The state group suffix. */
    private final String stateGroupSuffix;

    /**
     * Creates a state schema service.
     *
     * @param registryClient the registry client
     * @param groupId        the base group ID
     */
    public StateSchemaService(
            final RegistryClient registryClient,
            final String groupId) {
        this(registryClient, groupId, DEFAULT_STATE_GROUP_SUFFIX);
    }

    /**
     * Creates a state schema service with custom suffix.
     *
     * @param registryClient the registry client
     * @param groupId        the base group ID
     * @param suffix         the state group suffix
     */
    public StateSchemaService(
            final RegistryClient registryClient,
            final String groupId,
            final String suffix) {
        this.client = registryClient;
        this.baseGroupId = groupId;
        this.stateGroupSuffix = suffix;
    }

    /**
     * Registers a state schema for an operator.
     *
     * @param operatorId    the operator ID
     * @param schemaContent the schema content (Avro JSON)
     * @return the version number
     */
    public String registerStateSchema(
            final String operatorId,
            final String schemaContent) {
        final String stateGroup = getStateGroupId();
        final CreateVersion version = new CreateVersion();
        final VersionContent content = new VersionContent();
        content.setContent(schemaContent);
        content.setContentType("application/json");
        version.setContent(content);

        try {
            client.groups()
                    .byGroupId(stateGroup)
                    .artifacts()
                    .byArtifactId(operatorId)
                    .versions()
                    .post(version);

            final List<String> versions = listStateSchemaVersions(operatorId);
            return versions.isEmpty() ? "1"
                    : versions.get(versions.size() - 1);
        } catch (Exception e) {
            return createNewStateArtifact(operatorId, schemaContent);
        }
    }

    private String createNewStateArtifact(
            final String operatorId,
            final String schemaContent) {
        final String stateGroup = getStateGroupId();
        final CreateArtifact artifact = new CreateArtifact();
        artifact.setArtifactId(operatorId);
        artifact.setArtifactType("AVRO");

        final CreateVersion firstVersion = new CreateVersion();
        final VersionContent content = new VersionContent();
        content.setContent(schemaContent);
        content.setContentType("application/json");
        firstVersion.setContent(content);
        artifact.setFirstVersion(firstVersion);

        client.groups()
                .byGroupId(stateGroup)
                .artifacts()
                .post(artifact);
        return "1";
    }

    /**
     * Gets the latest state schema for an operator.
     *
     * @param operatorId the operator ID
     * @return the schema content
     */
    public String getLatestStateSchema(final String operatorId) {
        return getStateSchema(operatorId, "branch=latest");
    }

    /**
     * Gets a specific version of state schema.
     *
     * @param operatorId the operator ID
     * @param version    the version expression
     * @return the schema content
     */
    public String getStateSchema(
            final String operatorId,
            final String version) {
        try {
            final InputStream stream = client.groups()
                    .byGroupId(getStateGroupId())
                    .artifacts()
                    .byArtifactId(operatorId)
                    .versions()
                    .byVersionExpression(version)
                    .content()
                    .get();
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to get state schema: " + operatorId, e);
        }
    }

    /**
     * Lists all state schema versions for an operator.
     *
     * @param operatorId the operator ID
     * @return list of version numbers
     */
    public List<String> listStateSchemaVersions(final String operatorId) {
        try {
            final var result = client.groups()
                    .byGroupId(getStateGroupId())
                    .artifacts()
                    .byArtifactId(operatorId)
                    .versions()
                    .get();

            final List<String> versions = new ArrayList<>();
            if (result != null && result.getVersions() != null) {
                result.getVersions().forEach(
                        v -> versions.add(v.getVersion()));
            }
            return versions;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Checks if state schema exists for an operator.
     *
     * @param operatorId the operator ID
     * @return true if exists
     */
    public boolean hasStateSchema(final String operatorId) {
        try {
            client.groups()
                    .byGroupId(getStateGroupId())
                    .artifacts()
                    .byArtifactId(operatorId)
                    .get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the state group ID.
     *
     * @return the state group ID
     */
    public String getStateGroupId() {
        return baseGroupId + stateGroupSuffix;
    }

    /**
     * Gets the base group ID.
     *
     * @return the base group ID
     */
    public String getBaseGroupId() {
        return baseGroupId;
    }
}
