package io.apicurio.registry.rest;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

@Singleton
public class RestConfig {

    @ConfigProperty(name = "apicurio.rest.artifact.download.maxSize.bytes", defaultValue = "1000000")
    @Info(category = "rest", description = "Max size of the artifact allowed to be downloaded from URL", availableSince = "2.2.6-SNAPSHOT")
    int downloadMaxSize;

    @ConfigProperty(name = "apicurio.rest.artifact.download.sslValidation.disabled", defaultValue = "false")
    @Info(category = "rest", description = "Skip SSL validation when downloading artifacts from URL", availableSince = "2.2.6-SNAPSHOT")
    boolean downloadSkipSSLValidation;

    @Dynamic(label = "Delete artifact version", description = "When selected, users are permitted to delete artifact versions.")
    @ConfigProperty(name = "apicurio.rest.artifact.deletion.enabled", defaultValue = "false")
    @Info(category = "rest", description = "Enables artifact version deletion", availableSince = "2.4.2-SNAPSHOT")
    Supplier<Boolean> artifactVersionDeletionEnabled;

    public int getDownloadMaxSize() {
        return this.downloadMaxSize;
    }

    public boolean getDownloadSkipSSLValidation() {
        return this.downloadSkipSSLValidation;
    }

    public boolean isArtifactVersionDeletionEnabled() {
        return artifactVersionDeletionEnabled.get();
    }

}
