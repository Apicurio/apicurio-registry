package io.apicurio.registry.operator.updater;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.DataSourceSpec;
import io.apicurio.registry.operator.api.v1.spec.DeprecatedDataSource;
import io.apicurio.registry.operator.api.v1.spec.DeprecatedSqlSpec;
import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import io.apicurio.registry.operator.api.v1.spec.SqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;

public class SqlCRUpdater {

    private static final Logger log = LoggerFactory.getLogger(SqlCRUpdater.class);

    private static final String DEFAULT_SECRET_PASSWORD_FIELD = "password";

    /**
     * @return true if the CR has been updated
     */
    @SuppressWarnings("deprecation")
    public static boolean update(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        var updated = false;

        var storageType = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getType);

        var oldDataSource = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getSql)
                .map(DeprecatedSqlSpec::getDataSource);
        var oldUrl = oldDataSource
                .map(DeprecatedDataSource::getUrl)
                .filter(u -> !isBlank(u));
        var oldUsername = oldDataSource
                .map(DeprecatedDataSource::getUsername)
                .filter(u -> !isBlank(u));
        var oldPassword = oldDataSource
                .map(DeprecatedDataSource::getPassword);

        var newDataSource = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getSql)
                .map(SqlSpec::getDataSource);
        var newUrl = newDataSource
                .map(DataSourceSpec::getUrl)
                .filter(u -> !isBlank(u));
        var newUsername = newDataSource
                .map(DataSourceSpec::getUsername)
                .filter(u -> !isBlank(u));
        var newPassword = newDataSource
                .map(DataSourceSpec::getPassword);

        if (oldUrl.isPresent()) {
            log.warn("CR field `app.sql.dataSource.url` is DEPRECATED and should not be used.");
            if (newUrl.isEmpty() || oldUrl.equals(newUrl)) { // We need to handle a situation where the fields are partially migrated.
                if (storageType.isEmpty() || storageType.map(StorageType::isSqlStorageType).orElse(false)) {

                    log.info("Performing automatic CR update from `app.sql.dataSource.url` to `app.storage.sql.dataSource.url`.");
                    primary.getSpec().withApp().withStorage().setType(storageType.orElse(null));
                    primary.getSpec().getApp().getSql().getDataSource().setUrl(null);
                    primary.getSpec().getApp().getStorage().withSql().withDataSource().setUrl(oldUrl.get());

                    updated = true;
                } else {
                    storageTypeWarn();
                }
            } else {
                log.warn("Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.url` is already set.");
            }
        }

        if (oldUsername.isPresent()) {
            log.warn("CR field `app.sql.dataSource.username` is DEPRECATED and should not be used.");
            if (newUsername.isEmpty() || oldUsername.equals(newUsername)) { // We need to handle a situation where the fields are partially migrated.
                if (storageType.isEmpty() || storageType.map(StorageType::isSqlStorageType).orElse(false)) {

                    log.info("Performing automatic CR update from `app.sql.dataSource.username` to `app.storage.sql.dataSource.username`.");
                    primary.getSpec().withApp().withStorage().setType(storageType.orElse(StorageType.POSTGRESQL));
                    primary.getSpec().getApp().getStorage().withSql().withDataSource().setUsername(oldUsername.get());
                    primary.getSpec().getApp().getSql().getDataSource().setUsername(null);

                    updated = true;
                } else {
                    storageTypeWarn();
                }
            } else {
                log.warn("Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.username` is already set.");
            }
        }
        if (oldPassword.isPresent()) {
            log.warn("CR field `app.sql.dataSource.password` is DEPRECATED and should not be used.");

            // Get existing Secret, if it exists. We need to handle a situation where the fields are partially migrated.
            var newPasswordValue = newPassword
                    .map(x -> context.getClient()
                            .secrets()
                            .inNamespace(primary.getMetadata().getNamespace())
                            .withName(x.getName())
                            .get()
                    )
                    .map(x -> x.getData().get(DEFAULT_SECRET_PASSWORD_FIELD));

            if (newPassword.isEmpty() || oldPassword.equals(newPasswordValue)) {
                if (storageType.isEmpty() || storageType.map(StorageType::isSqlStorageType).orElse(false)) {

                    log.info("Performing automatic CR update from `app.sql.dataSource.password` to `app.storage.sql.dataSource.password`.");
                    primary.getSpec().withApp().withStorage().setType(storageType.orElse(StorageType.POSTGRESQL));
                    if (newPasswordValue.isEmpty()) {
                        // Create the Secret
                        var secretName = primary.getMetadata().getName() + "-datasource-password-" + randomUUID().toString().substring(0, 7);
                        // @formatter:off
                        var secret = new SecretBuilder()
                                .withNewMetadata()
                                    .withNamespace(primary.getMetadata().getNamespace())
                                    .withName(secretName)
                                .endMetadata()
                                .addToData(DEFAULT_SECRET_PASSWORD_FIELD, oldPassword.get())
                                .build();
                        // @formatter:on
                        context.getClient().resource(secret).create();
                        primary.getSpec().getApp().getStorage().withSql().withDataSource().setPassword(SecretKeyRef.builder().name(secretName).build());
                    }
                    primary.getSpec().getApp().getSql().getDataSource().setPassword(null);

                    updated = true;
                } else {
                    storageTypeWarn();
                }
            } else {
                log.warn("Automatic update cannot be performed, because the target field `app.storage.sql.dataSource.password` is already set.");
            }
        }

        oldDataSource = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getSql)
                .map(DeprecatedSqlSpec::getDataSource);
        oldUrl = oldDataSource
                .map(DeprecatedDataSource::getUrl)
                .filter(u -> !isBlank(u));
        oldUsername = oldDataSource
                .map(DeprecatedDataSource::getUsername)
                .filter(u -> !isBlank(u));
        oldPassword = oldDataSource
                .map(DeprecatedDataSource::getPassword);

        if (oldUrl.isEmpty() && oldUsername.isEmpty() && oldPassword.isEmpty()) {
            ofNullable(primary.getSpec())
                    .map(ApicurioRegistry3Spec::getApp)
                    .ifPresent(app -> app.setSql(null));
        }

        return updated;
    }

    private static void storageTypeWarn() {
        log.warn("Automatic update cannot be performed, because the field `app.storage.type` is already set and is not a SQL storage type (postgresql or mysql).");
    }
}
