package io.apicurio.registry.operator.updater;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.DeprecatedKafkasqlSpec;
import io.apicurio.registry.operator.api.v1.spec.KafkaSqlSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageSpec;
import io.apicurio.registry.operator.api.v1.spec.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KafkaSqlCRUpdater {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlCRUpdater.class);

    /**
     * @return true if the CR has been updated
     */
    @SuppressWarnings("deprecation")
    public static boolean update(ApicurioRegistry3 primary) {
        var updated = false;

        var storageType = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getType);

        var oldBootstrapServers = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getKafkasql)
                .map(DeprecatedKafkasqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));

        var newBootstrapServers = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage)
                .map(StorageSpec::getKafkasql)
                .map(KafkaSqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));

        if (oldBootstrapServers.isPresent()) {
            log.warn("CR field `app.kafkasql.boostrapServers` is DEPRECATED and should not be used.");
            if (newBootstrapServers.isEmpty() || oldBootstrapServers.equals(newBootstrapServers)) { // We need to handle a situation where the fields are partially migrated.
                if (storageType.isEmpty() || StorageType.KAFKASQL.equals(storageType.orElse(null))) {

                    log.info("Performing automatic CR update from `app.kafkasql.bootstrapServers` to `app.storage.kafkasql.bootstrapServers`.");
                    primary.getSpec().getApp().withStorage().setType(StorageType.KAFKASQL);
                    primary.getSpec().getApp().getStorage().withKafkasql().setBootstrapServers(oldBootstrapServers.get());
                    primary.getSpec().getApp().getKafkasql().setBootstrapServers(null);

                    updated = true;
                } else {
                    storageTypeWarn();
                }
            } else {
                log.warn("Automatic update cannot be performed, because the target field `app.storage.kafkasql.bootstrapServers` is already set.");
            }
        }

        oldBootstrapServers = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getKafkasql)
                .map(DeprecatedKafkasqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));

        if (oldBootstrapServers.isEmpty()) {
            ofNullable(primary.getSpec())
                    .map(ApicurioRegistry3Spec::getApp)
                    .ifPresent(app -> app.setKafkasql(null));
        }

        return updated;
    }

    private static void storageTypeWarn() {
        log.warn("Automatic update cannot be performed, because the field `app.storage.type` is already set and is not '{}'.", StorageType.KAFKASQL.getValue());
    }
}
