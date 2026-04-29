package io.apicurio.registry.resolver.telemetry;

public class UsageTelemetryEvent {

    private final String clientId;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final long globalId;
    private final String operation;
    private final long timestamp;

    public UsageTelemetryEvent(String clientId, String groupId, String artifactId, String version,
                               long globalId, String operation, long timestamp) {
        this.clientId = clientId;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.globalId = globalId;
        this.operation = operation;
        this.timestamp = timestamp;
    }

    public String getClientId() {
        return clientId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public long getGlobalId() {
        return globalId;
    }

    public String getOperation() {
        return operation;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
