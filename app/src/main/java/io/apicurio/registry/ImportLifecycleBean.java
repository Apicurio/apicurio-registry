package io.apicurio.registry;

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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    @ConfigProperty(name = "registry.import")
    Optional<String> registryImportZipPath;

    void onStart(@Observes StartupEvent ev) {
        if (registryImportZipPath.isPresent()) {
            try (final InputStream registryImportZip = new FileInputStream(registryImportZipPath.get())) {
                final ZipInputStream zip = new ZipInputStream(registryImportZip, StandardCharsets.UTF_8);
                final EntityReader reader = new EntityReader(zip);
                try (EntityInputStream stream = new EntityInputStream() {
                    @Override
                    public Entity nextEntity() {
                        try {
                            return reader.readEntity();
                        } catch (Exception e) {
                            log.error("Error reading data from import ZIP file {}.", registryImportZipPath, e);
                            return null;
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        zip.close();
                    }
                }) {
                    storage.importData(stream);
                    log.info("Registry successfully imported from {}", registryImportZipPath);
                }
            } catch (IOException ioe) {
                log.warn("Registry import from {} failed", registryImportZipPath, ioe);
            }
        }
    }

}
