package io.apicurio.registry.storage.importing;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URL;
import java.util.Optional;

@Singleton
public class ImportExportConfigProperties {

    @ConfigProperty(name = "apicurio.import.workDir")
    @Info(category = "import", description = "Temporary work directory to use when importing data.", availableSince = "3.0.0")
    public String workDir;

    @ConfigProperty(name = "apicurio.import.requireEmptyRegistry", defaultValue = "true")
    @Info(category = "import", description = "When set to true, importing data will only work when the registry is empty.  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean requireEmptyRegistry;

    @ConfigProperty(name = "apicurio.import.preserveGlobalId", defaultValue = "true")
    @Info(category = "import", description = "When set to true, global IDs from the import file will be used (otherwise new IDs will be generated).  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean preserveGlobalId;

    @ConfigProperty(name = "apicurio.import.preserveContentId", defaultValue = "true")
    @Info(category = "import", description = "When set to true, content IDs from the import file will be used (otherwise new IDs will be generated).  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean preserveContentId;

    @ConfigProperty(name = "apicurio.import.url")
    @Info(category = "import", description = "The import URL", availableSince = "2.1.0.Final")
    public Optional<URL> registryImportUrlProp;

}
