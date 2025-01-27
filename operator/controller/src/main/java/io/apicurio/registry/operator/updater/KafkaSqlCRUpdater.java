package io.apicurio.registry.operator.updater;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class KafkaSqlCRUpdater {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlCRUpdater.class);

    /**
     * @return true if the CR has been updated
     */
    public static boolean update(ApicurioRegistry3 primary) {
        var updated = false;
        var prevBootstrapServers = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getKafkasql).map(DeprecatedKafkasqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));
        var storageType = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage).map(StorageSpec::getType);
        var bootstrapServers = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getStorage).map(StorageSpec::getKafkasql).map(KafkaSqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));
        if (prevBootstrapServers.isPresent()) {
            log.warn("CR field `app.kafkasql.boostrapServers` is DEPRECATED and should not be used.");
            // Attempt to migrate the field and set the storage type
            if (bootstrapServers.isEmpty()) {
                if (storageType.isEmpty() || StorageType.KAFKASQL.equals(storageType.orElse(null))) {
                    log.info(
                            "Performing automatic CR update from `app.kafkasql.bootstrapServers` to `app.storage.kafkasql.bootstrapServers`.");
                    primary.getSpec().getApp().withStorage().setType(StorageType.KAFKASQL);
                    primary.getSpec().getApp().getKafkasql().setBootstrapServers(null);
                    primary.getSpec().getApp().getStorage().withKafkasql()
                            .setBootstrapServers(prevBootstrapServers.get());
                    updated = true;
                } else {
                    log.warn(
                            "Automatic update cannot be performed, "
                                    + "because the field `app.storage.type` is already set and is not '{}'.",
                            StorageType.KAFKASQL.getValue());
                }
            } else {
                log.warn(
                        "Automatic update cannot be performed, because the target field `app.storage.kafkasql.bootstrapServers` is already set.");
            }
        }
        // Set app.kafkasql to null if empty, to remove it from the CR
        prevBootstrapServers = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getKafkasql).map(DeprecatedKafkasqlSpec::getBootstrapServers)
                .filter(x -> !isBlank(x));
        if (prevBootstrapServers.isEmpty()) {
            ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .ifPresent(app -> app.setKafkasql(null));
        }
        return updated;
    }
}
