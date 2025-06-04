package io.apicurio.registry.rest;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_REST;

@Singleton
public class RestConfig {

    @ConfigProperty(name = "apicurio.rest.artifact.download.max-size.bytes", defaultValue = "1000000")
    @Info(category = CATEGORY_REST, description = "Max size of the artifact allowed to be downloaded from URL", availableSince = "2.2.6")
    int downloadMaxSize;

    @ConfigProperty(name = "apicurio.rest.search-results.labels.max-size.bytes", defaultValue = "512")
    @Info(category = CATEGORY_REST, description = "Max size of the labels (in bytes) per item from within search results", availableSince = "3.0.3")
    int labelsInSearchResultsMaxSize;

    @ConfigProperty(name = "apicurio.rest.artifact.download.ssl-validation.disabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Skip SSL validation when downloading artifacts from URL", availableSince = "2.2.6")
    boolean downloadSkipSSLValidation;

    @Dynamic(label = "Delete group", description = "When selected, users are permitted to delete groups.")
    @ConfigProperty(name = "apicurio.rest.deletion.group.enabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Enables group deletion", availableSince = "3.0.0")
    Supplier<Boolean> groupDeletionEnabled;

    @Dynamic(label = "Delete artifact", description = "When selected, users are permitted to delete artifacts.")
    @ConfigProperty(name = "apicurio.rest.deletion.artifact.enabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Enables artifact deletion", availableSince = "3.0.0")
    Supplier<Boolean> artifactDeletionEnabled;

    @Dynamic(label = "Delete artifact version", description = "When selected, users are permitted to delete artifact versions.")
    @ConfigProperty(name = "apicurio.rest.deletion.artifact-version.enabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Enables artifact version deletion", availableSince = "2.4.2")
    Supplier<Boolean> artifactVersionDeletionEnabled;

    @Dynamic(label = "Update artifact version content", description = "When selected, users are permitted to update the content of artifact versions (only when in the DRAFT state).")
    @ConfigProperty(name = "apicurio.rest.mutability.artifact-version-content.enabled", defaultValue = "false")
    @Info(category = CATEGORY_REST, description = "Enables artifact version mutability", availableSince = "3.0.2")
    Supplier<Boolean> artifactVersionMutabilityEnabled;

    public int getDownloadMaxSize() {
        return this.downloadMaxSize;
    }

    public int getLabelsInSearchResultsMaxSize() {
        return this.labelsInSearchResultsMaxSize;
    }

    public boolean getDownloadSkipSSLValidation() {
        return this.downloadSkipSSLValidation;
    }

    public boolean isGroupDeletionEnabled() {
        return groupDeletionEnabled.get();
    }

    public boolean isArtifactDeletionEnabled() {
        return artifactDeletionEnabled.get();
    }

    public boolean isArtifactVersionDeletionEnabled() {
        return artifactVersionDeletionEnabled.get();
    }

    public boolean isArtifactVersionMutabilityEnabled() {
        return artifactVersionMutabilityEnabled.get();
    }

}
