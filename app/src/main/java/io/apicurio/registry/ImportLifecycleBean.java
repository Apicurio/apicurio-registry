package io.apicurio.registry;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityReader;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class ImportLifecycleBean {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    StartupConfig startupConfig;

    void onStart(@Observes StartupEvent ev) {
        if (startupConfig.hasImportUrl()) {
            log.info("Import URL exists.");
            final URL registryImportUrl = startupConfig.getImportUrl();
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
                    storage.importData(stream);
                    log.info("Registry successfully imported from {}", registryImportUrl);
                }
            } catch (IOException ioe) {
                log.warn("Registry import from {} failed", registryImportUrl, ioe);
            }
        }
    }

}
