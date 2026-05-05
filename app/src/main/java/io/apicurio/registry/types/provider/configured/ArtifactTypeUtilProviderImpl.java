package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.ConfigPropertyCategory;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.config.artifactTypes.ArtifactTypesConfiguration;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.utils.IoUtil;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Default
@ApplicationScoped
public class ArtifactTypeUtilProviderImpl extends DefaultArtifactTypeUtilProviderImpl {

    @Inject
    Logger log;

    @Inject
    HttpClientService httpClientService;

    @ConfigProperty(name = "apicurio.artifact-types.config-file", defaultValue = "/tmp/apicurio-registry-artifact-types.json")
    @Info(category = ConfigPropertyCategory.CATEGORY_TYPES, description = "Path to a configuration file containing a list of supported artifact types.", availableSince = "3.1.0")
    @Getter
    private String configFile;

    @PostConstruct
    public void init() {
        // Try to load from external config file for user-defined custom types
        ArtifactTypesConfiguration config = loadArtifactTypeConfiguration();
        if (config != null) {
            loadConfiguredProviders(config);
            return;
        }

        // No external config — use standard providers (includes all built-in types)
        loadStandardProviders();
    }

    private ArtifactTypesConfiguration loadArtifactTypeConfiguration() {
        File file = new File(configFile);
        if (!file.isFile()) {
            return null;
        }

        try {
            log.info("Loading artifact type config from: {}", configFile);
            String configSrc = IoUtil.toString(file);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(configSrc, ArtifactTypesConfiguration.class);
        } catch (Exception e) {
            log.error("Failed to load artifact type configuration file", e);
        }

        return null;
    }

    private void loadConfiguredProviders(ArtifactTypesConfiguration config) {
        Set<String> artifactTypes = new HashSet<>();

        if (config.getIncludeStandardArtifactTypes()) {
            loadStandardProviders();
            standardProviders.forEach(provider -> {
                artifactTypes.add(provider.getArtifactType());
            });
        }

        if (!config.getArtifactTypes().isEmpty()) {
            config.getArtifactTypes().forEach(artifactType -> {
                if (Objects.isNull(artifactType.getName()) || artifactType.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration: Artifact type '" + artifactType.getArtifactType() + "' found with missing or empty 'name'.");
                }

                String type = artifactType.getArtifactType();
                if (Objects.isNull(type) || type.trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration: Artifact type named '" + artifactType.getName() + "' has missing or empty 'artifactType' property.");
                }

                if (artifactTypes.contains(type)) {
                    throw new IllegalArgumentException("Invalid configuration: Duplicate artifactType '" + type + "' found.");
                }

                log.info("Adding artifact type {}", artifactType.getName());
                providers.add(new ConfiguredArtifactTypeUtilProvider(httpClientService, artifactType));
                artifactTypes.add(type);
            });
        }
    }

}
