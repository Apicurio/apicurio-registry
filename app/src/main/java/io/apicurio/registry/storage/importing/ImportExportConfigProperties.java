package io.apicurio.registry.storage.importing;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class ImportExportConfigProperties {

    @ConfigProperty(name = "apicurio.import.workDir")
    @Info(category = "import", description = "Temporary work directory to use when importing data.", availableSince = "3.0.0")
    public String workDir;

}
