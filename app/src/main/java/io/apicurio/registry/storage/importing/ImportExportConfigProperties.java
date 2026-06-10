package io.apicurio.registry.storage.importing;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URL;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_IMPORT;

@Singleton
public class ImportExportConfigProperties {

    @ConfigProperty(name = "apicurio.import.work-dir")
    @Info(category = CATEGORY_IMPORT, description = "Temporary work directory to use when importing data.", availableSince = "3.0.0")
    public String workDir;

    @ConfigProperty(name = "apicurio.import.requireEmptyRegistry", defaultValue = "true")
    @Info(category = CATEGORY_IMPORT, description = "When set to true, importing data will only work when the registry is empty.  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean requireEmptyRegistry;

    @ConfigProperty(name = "apicurio.import.preserveGlobalId", defaultValue = "true")
    @Info(category = CATEGORY_IMPORT, description = "When set to true, global IDs from the import file will be used (otherwise new IDs will be generated).  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean preserveGlobalId;

    @ConfigProperty(name = "apicurio.import.preserveContentId", defaultValue = "true")
    @Info(category = CATEGORY_IMPORT, description = "When set to true, content IDs from the import file will be used (otherwise new IDs will be generated).  Defaults to 'true'.", availableSince = "3.0.0")
    public boolean preserveContentId;

    @ConfigProperty(name = "apicurio.import.url")
    @Info(category = CATEGORY_IMPORT, description = "The import URL", availableSince = "2.1.0.Final")
    public Optional<URL> registryImportUrlProp;

    @ConfigProperty(name = "apicurio.import.zip.max-entry-size", defaultValue = "1073741824")
    @Info(category = CATEGORY_IMPORT, description = "Maximum decompressed size in bytes allowed for a single ZIP entry during import. Defaults to 1 GiB.", availableSince = "3.0.7")
    public long zipMaxEntrySize;

    @ConfigProperty(name = "apicurio.import.zip.max-total-size", defaultValue = "1073741824")
    @Info(category = CATEGORY_IMPORT, description = "Maximum total decompressed size in bytes allowed across all ZIP entries during import. Defaults to 1 GiB.", availableSince = "3.0.7")
    public long zipMaxTotalSize;

    @ConfigProperty(name = "apicurio.import.zip.max-entry-count", defaultValue = "100000")
    @Info(category = CATEGORY_IMPORT, description = "Maximum number of ZIP entries allowed during import. Defaults to 100,000.", availableSince = "3.0.7")
    public int zipMaxEntryCount;

}
