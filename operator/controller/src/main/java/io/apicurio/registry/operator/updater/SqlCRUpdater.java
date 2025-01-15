package io.apicurio.registry.operator.updater;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class SqlCRUpdater {

    private static final Logger log = LoggerFactory.getLogger(SqlCRUpdater.class);

    /**
     * @return true if the CR has been updated
     */
    public static boolean update(ApicurioRegistry3 primary) {
        var updated = false;
        var prevDataSource = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getSql).map(DeprecatedSqlSpec::getDataSource);
        var prevUrl = prevDataSource.map(DeprecatedDataSource::getUrl).filter(u -> !isBlank(u));
        var prevUsername = prevDataSource.map(DeprecatedDataSource::getUsername).filter(u -> !isBlank(u));
        var prevPassword = prevDataSource.map(DeprecatedDataSource::getPassword);
        var storageType = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage).map(StorageSpec::getType);
        var dataSource = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage).map(StorageSpec::getSql).map(SqlSpec::getDataSource);
        var url = dataSource.map(DataSourceSpec::getUrl).filter(u -> !isBlank(u));
        var username = dataSource.map(DataSourceSpec::getUsername).filter(u -> !isBlank(u));
        var password = dataSource.map(DataSourceSpec::getPassword);
        if (prevUrl.isPresent()) {
            log.warn("CR field `app.sql.dataSource.url` is DEPRECATED and should not be used.");
            // Attempt to migrate the field and set the storage type
            if (url.isEmpty()) {
                if (storageType.isEmpty() || StorageType.POSTGRESQL.equals(storageType.orElse(null))) {
                    log.info(
                            "Performing automatic CR update from `app.sql.dataSource.url` to `app.storage.sql.dataSource.url`.");
                    primary.getSpec().getApp().withStorage().setType(StorageType.POSTGRESQL);
                    primary.getSpec().getApp().getSql().getDataSource().setUrl(null);
                    primary.getSpec().getApp().getStorage().withSql().withDataSource().setUrl(prevUrl.get());
                    updated = true;
                } else {
                    log.warn(
                            "Automatic update cannot be performed, "
                                    + "because the field `app.storage.type` is already set and is not '{}'.",
                            StorageType.POSTGRESQL.getValue());
                }
            } else {
                log.warn(
                        "Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.url` is already set.");
            }
        }
        if (prevUsername.isPresent()) {
            log.warn("CR field `app.sql.dataSource.username` is DEPRECATED and should not be used.");
            // Attempt to migrate the field and set the storage type
            if (username.isEmpty()) {
                if (storageType.isEmpty() || StorageType.POSTGRESQL.equals(storageType.orElse(null))) {
                    log.info(
                            "Performing automatic CR update from `app.sql.dataSource.username` to `app.storage.sql.dataSource.username`.");
                    primary.getSpec().withApp().withStorage().setType(StorageType.POSTGRESQL);
                    primary.getSpec().getApp().getSql().getDataSource().setUsername(null);
                    primary.getSpec().getApp().getStorage().withSql().withDataSource()
                            .setUsername(prevUsername.get());
                    updated = true;
                } else {
                    log.warn(
                            "Automatic update cannot be performed, "
                                    + "because the field `app.storage.type` is already set and is not '{}'.",
                            StorageType.POSTGRESQL.getValue());
                }
            } else {
                log.warn(
                        "Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.username` is already set.");
            }
        }
        if (prevPassword.isPresent()) {
            log.warn("CR field `app.sql.dataSource.password` is DEPRECATED and should not be used.");
            // Attempt to migrate the field and set the storage type
            if (password.isEmpty()) {
                if (storageType.isEmpty() || StorageType.POSTGRESQL.equals(storageType.orElse(null))) {
                    log.info(
                            "Performing automatic CR update from `app.sql.dataSource.password` to `app.storage.sql.dataSource.password`.");
                    primary.getSpec().withApp().withStorage().setType(StorageType.POSTGRESQL);
                    primary.getSpec().getApp().getSql().getDataSource().setPassword(null);
                    primary.getSpec().getApp().getStorage().withSql().withDataSource()
                            .setPassword(prevPassword.get());
                    updated = true;
                } else {
                    log.warn(
                            "Automatic update cannot be performed, "
                                    + "because the field `app.storage.type` is already set and is not '{}'.",
                            StorageType.POSTGRESQL.getValue());
                }
            } else {
                log.warn(
                        "Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.password` is already set.");
            }
        }
        // Set app.sql to null if empty, to remove it from the CR
        prevDataSource = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp).map(AppSpec::getSql)
                .map(DeprecatedSqlSpec::getDataSource);
        prevUrl = prevDataSource.map(DeprecatedDataSource::getUrl).filter(u -> !isBlank(u));
        prevUsername = prevDataSource.map(DeprecatedDataSource::getUsername).filter(u -> !isBlank(u));
        prevPassword = prevDataSource.map(DeprecatedDataSource::getPassword);
        if (prevUrl.isEmpty() && prevUsername.isEmpty() && prevPassword.isEmpty()) {
            ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .ifPresent(app -> app.setSql(null));
        }
        return updated;
    }
}
