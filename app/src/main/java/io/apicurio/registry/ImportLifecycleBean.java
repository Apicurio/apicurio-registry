package io.apicurio.registry;

import io.apicurio.registry.rest.ConflictException;
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
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@ApplicationScoped
public class ImportLifecycleBean {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ImportExportConfigProperties importExportProps;

    @Inject
    AdminResourceImpl v3Admin;

    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (StorageEventType.READY.equals(ev.getType())
                && importExportProps.registryImportUrlProp.isPresent()) {
            log.info("Import URL exists.");
            final URL registryImportUrl = importExportProps.registryImportUrlProp.get();
            try (final InputStream registryImportZip = new BufferedInputStream(
                    registryImportUrl.openStream())) {
                log.info("Importing {} on startup.", registryImportUrl);
                v3Admin.importData(null, null, null, registryImportZip);
                log.info("Registry successfully imported from {}", registryImportUrl);
            } catch (IOException ioe) {
                log.error("Registry import from {} failed", registryImportUrl, ioe);
            } catch (ReadOnlyStorageException rose) {
                log.error("Registry import failed, because the storage is in read-only mode.");
            } catch (ConflictException ce) {
                log.info("Import skipped, registry not empty.");
            } catch (Exception e) {
                log.error("Registry import failed", e);
            }
        }
    }

}
