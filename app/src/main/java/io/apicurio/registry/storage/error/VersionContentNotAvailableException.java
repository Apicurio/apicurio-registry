package io.apicurio.registry.storage.error;

import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.types.VersionState;
import lombok.Getter;

@SuppressWarnings("java:S110") // Inheritance depth is necessary to preserve the project's 404 error mapping
public class VersionContentNotAvailableException extends NotFoundException {

    private static final long serialVersionUID = -6505026249936380069L;

    @Getter
    private final String groupId;

    @Getter
    private final String artifactId;

    @Getter
    private final String version;

    @Getter
    private final VersionState state;

    public VersionContentNotAvailableException(String groupId, String artifactId, String version,
            VersionState state) {
        super(message(groupId, artifactId, version, state));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.state = state;
    }

    private static String message(String groupId, String artifactId, String version, VersionState state) {
        return "Content for version '" + version + "' of artifact with ID '" + artifactId + "' in group '"
                + GroupId.displayName(groupId) + "' is not available while its state is '" + state + "'.";
    }
}
