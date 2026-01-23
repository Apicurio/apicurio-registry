package io.apicurio.registry.storage.impl.sql.repositories;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.error.DownloadNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository handling download operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlDownloadRepository {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, true);
    }

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Create a new download entry.
     */
    public String createDownload(DownloadContextDto context) throws RegistryStorageException {
        log.debug("Inserting a download.");
        String downloadId = UUID.randomUUID().toString();
        return handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.insertDownload())
                    .bind(0, downloadId)
                    .bind(1, context.getExpires())
                    .bind(2, mapper.writeValueAsString(context))
                    .execute();
            return downloadId;
        });
    }

    /**
     * Consume a download (get and delete).
     */
    public DownloadContextDto consumeDownload(String downloadId) throws RegistryStorageException {
        log.debug("Consuming a download ID: {}", downloadId);

        return handles.withHandleNoException(handle -> {
            long now = System.currentTimeMillis();

            // Select the download context.
            Optional<String> res = handle.createQuery(sqlStatements.selectDownloadContext())
                    .bind(0, downloadId)
                    .bind(1, now)
                    .mapTo(String.class)
                    .findOne();
            String downloadContext = res.orElseThrow(DownloadNotFoundException::new);

            // Attempt to delete the row.
            int rowCount = handle.createUpdate(sqlStatements.deleteDownload())
                    .bind(0, downloadId)
                    .execute();
            if (rowCount == 0) {
                throw new DownloadNotFoundException();
            }

            // Return what we consumed
            return mapper.readValue(downloadContext, DownloadContextDto.class);
        });
    }

    /**
     * Delete all expired downloads.
     */
    public void deleteAllExpiredDownloads() throws RegistryStorageException {
        log.debug("Deleting all expired downloads");
        long now = System.currentTimeMillis();
        handles.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.deleteExpiredDownloads())
                    .bind(0, now)
                    .execute();
            return null;
        });
    }
}
