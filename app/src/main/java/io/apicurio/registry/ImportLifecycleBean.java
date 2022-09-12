package io.apicurio.registry;

import io.apicurio.common.apps.config.Info;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityReader;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipInputStream;

@ApplicationScoped
public class ImportLifecycleBean {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "registry.import.url")
    @Info(category = "import", description = "The import URL", availableSince = "2.1.0.Final")
    Optional<URL> registryImportUrlProp;

    void onStart(@Observes StartupEvent ev) {
        if (registryImportUrlProp.isPresent()) {
            log.info("Import URL exists.");
            final URL registryImportUrl = registryImportUrlProp.get();
            try (final InputStream registryImportZip = new BufferedInputStream(registryImportUrl.openStream())) {
                log.info("Importing {} on startup.", registryImportUrl);
                final ZipInputStream zip = new ZipInputStream(registryImportZip, StandardCharsets.UTF_8);
                final EntityReader reader = new EntityReader(zip);
                try (EntityInputStream stream = new EntityInputStream() {
                    @Override
                    public Entity nextEntity() {
                        try {
                            return reader.readEntity();
                        } catch (Exception e) {
                            log.error("Error reading data from import ZIP file {}.", registryImportUrl, e);
                            return null;
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        zip.close();
                    }
                }) {
                    storage.importData(stream, true, true);
                    log.info("Registry successfully imported from {}", registryImportUrl);
                }
            } catch (IOException ioe) {
                log.warn("Registry import from {} failed", registryImportUrl, ioe);
            }
        }
    }

}
