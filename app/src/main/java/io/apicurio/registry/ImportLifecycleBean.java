package io.apicurio.registry;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.rest.v3.AdminResourceImpl;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.importing.ImportExportConfigProperties;
import io.apicurio.registry.types.Current;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

@ApplicationScoped
public class ImportLifecycleBean {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ImportExportConfigProperties importExportProps;

    @ConfigProperty(name = "apicurio.import.url")
    @Info(category = "import", description = "The import URL", availableSince = "2.1.0.Final")
    Optional<URL> registryImportUrlProp;

    @Inject
    AdminResourceImpl v3Admin;

    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (StorageEventType.READY.equals(ev.getType()) && registryImportUrlProp.isPresent()) {
            log.info("Import URL exists.");
            final URL registryImportUrl = registryImportUrlProp.get();
            try (final InputStream registryImportZip = new BufferedInputStream(
                    registryImportUrl.openStream())) {
                log.info("Importing {} on startup.", registryImportUrl);
                v3Admin.importData(false, false, registryImportZip);
                log.info("Registry successfully imported from {}", registryImportUrl);
            } catch (IOException ioe) {
                log.error("Registry import from {} failed", registryImportUrl, ioe);
            } catch (ReadOnlyStorageException rose) {
                log.error("Registry import failed, because the storage is in read-only mode.");
            } catch (Exception e) {
                log.error("Registry import failed", e);
            }
        }
    }

}
