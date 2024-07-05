package io.apicurio.registry.storage.impl.kafkasql.upgrade;

import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager.UpgraderManagerHandle;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;

/**
 * Implementors of this interface provide upgrade functionality for KafkaSQL storage.
 * Please read the javadoc of {@link KafkaSqlUpgraderManager} as well.
 * <p>
 * Multitenancy considerations:
 * Methods on this class are not called with request context active.
 * Therefore, upgraders MUST NOT rely on multitenancy features, unless handled manually
 * (e.g. use @ActivateRequestContext and load a tenant manually).
 * As a result, they MUST be tenant agnostic.
 */
public interface KafkaSqlUpgrader extends AutoCloseable {

    /**
     * Some upgraders may require to see all messages in the topic before performing an upgrade.
     * This method is called for each message when the Registry starts and before the upgrade method is called.
     * Upgraders that build/keep state using this method MUST support releasing the resources
     * via the close() method to avoid resource leaks.
     * This method might also help with the reentrancy requirement of upgraders, so they can make
     * decisions based on the past messages in the topic.
     * <p>
     * This method SHOULD NOT throw exceptions, otherwise the startup will fail.
     */
    default void read(MessageKey key, MessageValue value) {
        // NOOP
    }


    /**
     * Return true for version(s) which need this upgrader to be executed.
     * <p>
     * This method SHOULD NOT throw exceptions, otherwise the startup will fail.
     */
    boolean supportsVersion(int currentVersion);


    /**
     * Run the upgrade process.
     * Upgrade process MUST fulfill the following requirements:
     * <ul>
     *   <li>It MUST be reentrant. The upgrader manager tries to provide exclusive access to a single upgrader
     *     on a single node, but an upgrader might fail and MUST support multiple retries.</li>
     *   <li>It SHOULD tolerate being accidentally executed on multiple nodes. The exclusive access is on best-effort-basis.</li>
     * </ul>
     * If the method throws an exception, startup will not fail, but the topic might not be fully upgraded.
     * Any actions performed by the upgrader up to that point are not reverted.
     * The upgrade will not be retried on *this* node, but another node might still successfully upgrade,
     * which is another reason why the upgraders MUST be reentrant.
     *
     * @param handle Upgrader must periodically call the {@code handle.heartbeat()} method to help the upgrade manager to keep the topic locked.
     *               You should call the method at least once per half of {@code handle.getLockTimeout()} duration,
     *               but it can be more. It is advisable to call it before and after a blocking operation.
     */
    void upgrade(UpgraderManagerHandle handle) throws Exception;


    /**
     * Close the upgrader and release any resources to avoid resource leaks.
     * Closed upgrader MUST NOT be called again.
     * Upgraders are closed even if their upgrade() method was not called.
     * <p>
     * Thrown exceptions are ignored.
     */
    @Override
    default void close() throws Exception {
        // NOOP
    }
}
