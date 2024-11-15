package io.apicurio.common.apps.storage.sql;

import io.apicurio.common.apps.content.handle.ContentHandle;
import io.apicurio.common.apps.logging.LoggerProducer;
import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.Handle;
import io.apicurio.common.apps.storage.sql.jdbi.HandleFactory;
import io.apicurio.common.apps.storage.sql.jdbi.parse.DdlParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class SqlStorageComponent {

    private static final String DB_PROPERTY_VERSION = "db_version";

    private HandleFactory handles;

    private Logger log;

    private volatile boolean isReady;
    private volatile boolean isStarting;

    private Configuration config;

    @Builder
    @Getter
    public static class Configuration {

        private Boolean supportsAtomicSequenceIncrement;

        private SqlStatements sqlStatements;

        private String ddlDirRootPath;

        @Builder.Default
        private Boolean initDB = true;

        @Builder.Default
        private Runnable onReady = () -> {
        };
    }

    @Transactional
    public synchronized void start(LoggerProducer loggerProducer, HandleFactory handles,
            Configuration config) {
        if (isStarting) {
            throw new RuntimeException("The BaseSqlStorageComponent can be started only once");
        }
        isStarting = true;
        this.log = loggerProducer.getLogger(getClass());
        this.handles = handles;
        requireNonNull(config.supportsAtomicSequenceIncrement);
        requireNonNull(config.sqlStatements);
        requireNonNull(config.ddlDirRootPath);
        requireNonNull(config.initDB);
        requireNonNull(config.onReady);
        this.config = config;

        log.info("Starting SQL storage.");

        handles.withHandleNoExceptionMapped(handle -> {

            var initialized = config.sqlStatements.isDatabaseInitialized(handle);

            if (!initialized) {
                if (config.initDB) {
                    log.info("Database not initialized.");
                    initializeDatabase(handle);
                } else {
                    log.error(
                            "Database not initialized.  Please use the DDL scripts to initialize the database before starting the application.");
                    throw new RuntimeException("Database not initialized.");
                }
            } else {
                log.info("Database was already initialized, skipping.");
            }

            if (!isDatabaseCurrent(handle)) {
                if (config.initDB) {
                    log.info("Old database version detected, upgrading.");
                    upgradeDatabase(handle);
                } else {
                    log.error(
                            "Detected an old version of the database.  Please use the DDL upgrade scripts to bring your database up to date.");
                    throw new RuntimeException("Database not upgraded.");
                }
            } else {
                log.info("Database is up to date.");
            }

            return null;
        });

        isReady = true;
        config.onReady.run();
    }

    private int getLatestDatabaseVersion() {
        var resource = getResource(config.ddlDirRootPath + "/version");
        return Integer.parseInt(resource.string());
    }

    /**
     * @return true if the database has already been initialized
     */
    private boolean isDatabaseCurrent(Handle handle) {
        log.info("Checking to see if the DB is up-to-date.");
        log.info("Build's DB version is {}", getLatestDatabaseVersion());
        int version = this.getDatabaseVersion();
        return version == getLatestDatabaseVersion();
    }

    private void initializeDatabase(Handle handle) throws StorageException {
        log.info("Initializing the Apicurio Registry database.");
        log.info("Database type: {}", config.sqlStatements.dbType());

        try {

            DdlParser parser = new DdlParser();
            var dbt = config.sqlStatements.dbType();
            var path = config.ddlDirRootPath + "/" + dbt + ".ddl";
            var resource = getResource(path);
            log.debug("DDL root path: {}", path);
            var statements = parser.parse(resource.stream());
            log.debug("---");
            for (String statement : statements) {
                log.debug(statement);
                handle.createUpdate(statement).execute();
            }
            this.setStorageProperty(DB_PROPERTY_VERSION, String.valueOf(getLatestDatabaseVersion()));
            log.debug("---");

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private ContentHandle getResource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new RuntimeException("Resource not found: " + path);
        }
        // No need to close the stream, ContentHandle will do it
        return ContentHandle.create(stream);
    }

    /**
     * Upgrades the database by executing a number of DDL statements found in DB-specific DDL upgrade scripts.
     */
    private void upgradeDatabase(Handle handle) throws StorageException {
        log.info("Upgrading the database.");

        int fromVersion = this.getDatabaseVersion();
        int toVersion = getLatestDatabaseVersion();

        log.info("\tDatabase type: {}", config.sqlStatements.dbType());
        log.info("\tFrom Version:  {}", fromVersion);
        log.info("\tTo Version:    {}", toVersion);

        for (int nextVersion = fromVersion + 1; nextVersion <= toVersion; nextVersion++) {
            try {
                log.info("Performing upgrade {} -> {}", nextVersion - 1, nextVersion);

                DdlParser parser = new DdlParser();
                var resource = getResource(config.ddlDirRootPath + "/upgrades/" + nextVersion + "/"
                        + config.sqlStatements.dbType() + ".upgrade.ddl");
                var statements = parser.parse(resource.stream());

                log.debug("---");
                for (String statement : statements) {
                    log.debug(statement);

                    if (statement.startsWith("UPGRADER:")) {
                        String cname = statement.substring(9).trim();
                        applyUpgrader(handle, cname);
                    } else {
                        handle.createUpdate(statement).execute();
                    }
                }
                setStorageProperty(DB_PROPERTY_VERSION, String.valueOf(nextVersion));
                log.debug("---");

            } catch (IOException ex) {
                log.error("Upgrade failed {} -> {}", nextVersion - 1, nextVersion);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Instantiates an instance of the given upgrader class and then invokes it. Used to perform advanced
     * upgrade logic when upgrading the DB (logic that cannot be handled in simple SQL statements).
     *
     * @param handle
     * @param cname
     */
    private void applyUpgrader(Handle handle, String cname) {
        try {
            @SuppressWarnings("unchecked")
            Class<IDbUpgrader> upgraderClass = (Class<IDbUpgrader>) Class.forName(cname);
            IDbUpgrader upgrader = upgraderClass.getConstructor().newInstance();
            upgrader.upgrade(handle);
        } catch (Exception ex) {
            throw new RuntimeException("Could not apply upgrader " + cname, ex);
        }
    }

    /**
     * Returns the current DB version or 0
     */
    private int getDatabaseVersion() {
        var versionRaw = getStorageProperty(DB_PROPERTY_VERSION);
        if (versionRaw != null) {
            try {
                return Integer.parseInt(versionRaw);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Property " + DB_PROPERTY_VERSION + " has an invalid value", ex);
            }
        } else {
            return 0;
        }
    }

    public boolean isReady() {
        return isReady;
    }

    @Transactional
    public String getStorageProperty(String key) {
        try {
            return handles.withHandle(handle -> handle.createQuery(config.sqlStatements.getStorageProperty())
                    .bind(0, key).mapTo(String.class).one());
        } catch (StorageException ex) {
            log.warn("Storage property " + key + " does not exist", ex);
            return null;
        }
    }

    @Transactional
    public void setStorageProperty(String key, String value) {
        handles.withHandleNoExceptionMapped(handle -> config.sqlStatements.setStorageProperty(key, value)
                .setHandleOnce(handle).execute());
    }

    /**
     * Try to atomically compute the next value in a sequence with the given name. Implements a workaround for
     * databases which do not implement atomic increments, like H2.
     */
    @Transactional
    public long nextSequenceValue(String sequenceKey) throws StorageException {
        if (config.supportsAtomicSequenceIncrement) {
            // In case the database supports atomic increments
            return handles.withHandleNoExceptionMapped(
                    handle -> handle.createQuery(config.sqlStatements.getNextSequenceValue())
                            .bind(0, sequenceKey).mapTo(Long.class).one());
        } else {
            // Attempt a CAS-like operation with 20 retries
            for (int retries = 1; retries <= 20; retries++) {
                try {
                    var result = tryNextSequenceValue(sequenceKey);
                    if (result.isPresent()) {
                        return result.get();
                    }
                } catch (Exception ex) {
                    if (retries == 20) {
                        throw ex; // Failed
                    }
                }
            }
            throw new StorageException("Could not get next value of sequence " + sequenceKey, null);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW) // We want a fresh transaction for each retry
    public Optional<Long> tryNextSequenceValue(String sequenceKey) throws StorageException {
        // Get the current value if exists
        Optional<Long> currentValue = handles.withHandleNoExceptionMapped(
                handle -> handle.createQuery(config.sqlStatements.getSequenceValue()).bind(0, sequenceKey)
                        .mapTo(Long.class).findOne());
        if (currentValue.isPresent()) {
            Long newValue = currentValue.get() + 1;
            // Try to update the value
            var affected = handles
                    .withHandle(handle -> handle.createUpdate(config.sqlStatements.casSequenceValue())
                            .bind(0, newValue).bind(1, sequenceKey).bind(2, currentValue.get()).execute());
            if (affected == 1) {
                return Optional.of(newValue);
            } else {
                return Optional.empty();
            }
        } else {
            // Try to insert an initial value
            handles.withHandle(handle -> handle.createUpdate(config.sqlStatements.insertSequenceValue())
                    .bind(0, sequenceKey).bind(1, 1).execute());
            return Optional.of(1L);
        }
    }
}
