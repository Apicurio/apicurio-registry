package io.apicurio.registry.serde.fallback;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.Map;

/**
 * Default implementation of FallbackArtifactProvider that simply uses config properties
 */
public class DefaultFallbackArtifactProvider implements FallbackArtifactProvider {

    private ArtifactReference fallbackArtifactReference;

    /**
     * @see io.apicurio.registry.serde.fallback.FallbackArtifactProvider#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {

        String groupIdConfigKey = SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID;
        if (isKey) {
            groupIdConfigKey += ".key";
        }
        String fallbackGroupId = (String) configs.get(groupIdConfigKey);

        String artifactIdConfigKey = SerdeConfig.FALLBACK_ARTIFACT_ID;
        if (isKey) {
            artifactIdConfigKey += ".key";
        }
        String fallbackArtifactId = (String) configs.get(artifactIdConfigKey);

        String versionConfigKey = SerdeConfig.FALLBACK_ARTIFACT_VERSION;
        if (isKey) {
            versionConfigKey += ".key";
        }
        String fallbackVersion = (String) configs.get(versionConfigKey);

        if (fallbackArtifactId != null) {
            fallbackArtifactReference = ArtifactReference.builder().groupId(fallbackGroupId)
                    .artifactId(fallbackArtifactId).version(fallbackVersion).build();
        }

    }

    /**
     * @see io.apicurio.registry.serde.fallback.FallbackArtifactProvider#get(java.lang.String, byte[])
     */
    @Override
    public ArtifactReference get(String topic, byte[] data) {
        return fallbackArtifactReference;
    }

    public boolean isConfigured() {
        return fallbackArtifactReference != null;
    }

}
