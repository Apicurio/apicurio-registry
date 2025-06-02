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

    private enum ImportStatus {
        UNKNOWN, SKIPPED, STARTED, COMPLETE, ERROR
    }

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ImportExportConfigProperties importExportProps;

    @Inject
    AdminResourceImpl v3Admin;

    private ImportStatus importStatus = ImportStatus.UNKNOWN;

    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (StorageEventType.READY.equals(ev.getType())) {
            if (importExportProps.registryImportUrlProp.isPresent()) {
                log.info("Import URL exists.");
                final URL registryImportUrl = importExportProps.registryImportUrlProp.get();
                importStatus = ImportStatus.STARTED;
                try (final InputStream registryImportZip = new BufferedInputStream(
                        registryImportUrl.openStream())) {
                    log.info("Importing {} on startup.", registryImportUrl);
                    v3Admin.importData(null, null, null, registryImportZip);
                    log.info("Registry successfully imported from {}", registryImportUrl);
                    importStatus = ImportStatus.COMPLETE;
                } catch (IOException ioe) {
                    log.error("Registry import from {} failed", registryImportUrl, ioe);
                    importStatus = ImportStatus.ERROR;
                } catch (ReadOnlyStorageException rose) {
                    log.error("Registry import failed, because the storage is in read-only mode.");
                    importStatus = ImportStatus.ERROR;
                } catch (ConflictException ce) {
                    log.info("Import skipped, registry not empty.");
                    importStatus = ImportStatus.ERROR;
                } catch (Exception e) {
                    log.error("Registry import failed", e);
                    importStatus = ImportStatus.ERROR;
                }
            } else {
                importStatus = ImportStatus.SKIPPED;
            }
        }
    }

    public boolean isReady() {
        return importStatus == ImportStatus.SKIPPED || importStatus == ImportStatus.COMPLETE;
    }
}
