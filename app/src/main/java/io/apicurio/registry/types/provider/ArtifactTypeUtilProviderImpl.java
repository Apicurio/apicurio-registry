package io.apicurio.registry.types.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Default
@ApplicationScoped
public class ArtifactTypeUtilProviderImpl extends DefaultArtifactTypeUtilProviderImpl {

    @Inject
    Logger log;

    @Inject
    HttpClientService httpClientService;

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
        if (config.getIncludeStandardArtifactTypes()) {
            loadStandardProviders();
        }
        if (!config.getArtifactTypes().isEmpty()) {
            config.getArtifactTypes().forEach(artifactType -> {
                log.info("Adding artifact type {}", artifactType.getName());
                providers.add(new ConfiguredArtifactTypeUtilProvider(httpClientService, artifactType));
            });
        }
        // TODO validate the config
        // 1. all artifact types must be unique (no duplicates)
        // 2. all artifact types must have a valid "artifactType" property
        // 2. all artifact types must have a valid "name" property
    }

}
