package io.apicurio.registry.upgrade.v2;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.v2.EntityReader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipInputStream;

/**
 * This class, once the storage is ready, checks if a v2 export file has been configured to restore the data
 * from an older server and, if there's one present, triggers the data upgrade process, eventually changing
 * the data structure and inserting the information into the database in v3 format.
 */
@ApplicationScoped
public class DataUpgrader {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @ConfigProperty(name = "apicurio.upgrade.file.location")
    @Info(category = "upgrade", description = "The URL to a v2 export file", availableSince = "3.0.0")
    Optional<URL> registryV2ExportFile;

    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (StorageEventType.READY.equals(ev.getType()) && registryV2ExportFile.isPresent()) {
            log.info("Registry V2 export file exists.");
            final URL registryV2ExportUrl = registryV2ExportFile.get();
            try (final InputStream registryV2ExportZip = new BufferedInputStream(
                    registryV2ExportUrl.openStream())) {
                log.info("Importing {} on startup.", registryV2ExportUrl);
                final ZipInputStream zip = new ZipInputStream(registryV2ExportZip, StandardCharsets.UTF_8);
                final EntityReader reader = new EntityReader(zip);
                try (EntityInputStream stream = new EntityInputStream() {
                    @Override
                    public Entity nextEntity() {
                        try {
                            return reader.readEntity();
                        } catch (Exception e) {
                            log.error("Error reading data from import ZIP file {}.", registryV2ExportUrl, e);
                            return null;
                        }
                    }

                    @Override
                    public void close() throws IOException {
                        zip.close();
                    }
                }) {
                    storage.upgradeData(stream, true, true);
                    log.info("Registry V2 data successfully upgraded and imported from {}",
                            registryV2ExportUrl);
                } catch (ReadOnlyStorageException e) {
                    log.error("Registry V2 data import failed, because the storage is in read-only mode.");
                }
            } catch (IOException ioe) {
                log.error("Registry V2 data import from {} failed", registryV2ExportUrl, ioe);
            }
        }
    }

}
