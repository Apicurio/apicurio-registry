package io.apicurio.registry.ccompat.store;

import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.storage.StoredArtifact;

public class FacadeConverter {

    public static int convertUnsigned(long value) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of unsigned integer range: " + value);
        }
        return (int) value;
    }

    public static Schema convert(String subject, StoredArtifact storedArtifact) {
        return new Schema(
                convertUnsigned(storedArtifact.getGlobalId()),
                subject,
                convertUnsigned(storedArtifact.getVersion().intValue()),
                storedArtifact.getContent().content()
        );
    }

    public static SchemaContent convert(StoredArtifact artifactVersion) {
        return new SchemaContent(artifactVersion.getContent().content());
    }
}
