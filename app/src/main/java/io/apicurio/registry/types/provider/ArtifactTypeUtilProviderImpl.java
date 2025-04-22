package io.apicurio.registry.types.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.config.artifactTypes.ArtifactTypesConfiguration;
import io.apicurio.registry.http.HttpClientService;
import io.apicurio.registry.script.ScriptingService;
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

    @Inject
    ScriptingService scriptingService;

    @ConfigProperty(name = "apicurio.artifact-types.config-file", defaultValue = "/tmp/apicurio-registry-artifact-types.json")
    @Info(category = "types", description = "Path to a configuration file containing a list of supported artifact types.", availableSince = "3.1.0")
    @Getter
    private String configFile;

    @PostConstruct
    public void init() {
        ArtifactTypesConfiguration config = loadArtifactTypeConfiguration();
        if (config != null) {
            loadConfiguredProviders(config);
        } else {
            log.info("Artifact type config file not found at {}, using standard types.", configFile);
            loadStandardProviders();
        }
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
        // The set of loaded artifact types.
        Set<String> artifactTypes = new HashSet<>();

        if (config.getIncludeStandardArtifactTypes()) {
            loadStandardProviders();
            standardProviders.forEach(provider -> {
                artifactTypes.add(provider.getArtifactType());
            });
        }

        if (!config.getArtifactTypes().isEmpty()) {
            // Process each configured artifact type.
            config.getArtifactTypes().forEach(artifactType -> {
                // All artifact types must have a valid, non-null "name" property
                if (Objects.isNull(artifactType.getName()) || artifactType.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration: Artifact type '" + artifactType.getArtifactType() + "' found with missing or empty 'name'.");
                }

                // All artifact types must have a valid and unique "artifactType" property
                String type = artifactType.getArtifactType();
                if (Objects.isNull(type) || type.trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid configuration: Artifact type named '" + artifactType.getName() + "' has missing or empty 'artifactType' property.");
                }

                // Artifact types must be unique.
                if (artifactTypes.contains(type)) {
                    throw new IllegalArgumentException("Invalid configuration: Duplicate artifactType '" + type + "' found.");
                }

                log.info("Adding artifact type {}", artifactType.getName());
                providers.add(new ConfiguredArtifactTypeUtilProvider(httpClientService, scriptingService, artifactType));
                artifactTypes.add(type);
            });
        }
    }

}
