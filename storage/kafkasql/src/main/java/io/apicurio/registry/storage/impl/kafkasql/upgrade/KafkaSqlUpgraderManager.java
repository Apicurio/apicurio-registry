/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.kafkasql.upgrade;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.exception.RuntimeAssertionFailedException;
import io.apicurio.registry.exception.UnreachableCodeException;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey;
import io.apicurio.registry.storage.impl.kafkasql.keys.UpgraderKey;
import io.apicurio.registry.storage.impl.kafkasql.sql.KafkaSqlStore;
import io.apicurio.registry.storage.impl.kafkasql.values.ActionType;
import io.apicurio.registry.storage.impl.kafkasql.values.MessageValue;
import io.apicurio.registry.storage.impl.kafkasql.values.UpgraderValue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * This class is responsible for managing KafkaSQL upgrade process,
 * and to determine when the storage is ready.
 * <p>
 * See {@link KafkaSqlUpgrader} to find out about guarantees that the upgrader manager provides.
 */
@ApplicationScoped
public class KafkaSqlUpgraderManager {

    /**
     * Version of the storage before this upgrade process was implemented.
     */
    public static final int BASE_KAFKASQL_TOPIC_VERSION = 1;

    /**
     * Version of the storage this *specific* upgrader should upgrade to.
     * Increment this counter when upgrade is needed.
     * <p>
     * WARNING: Incrementing this counter even if there are no active upgraders should work,
     * but is not recommended, as it unnecessarily adds messages to the topic and consumes CPU and time.
     */
    public static final int TARGET_KAFKASQL_TOPIC_VERSION = 2;

    /**
     * How long should KafkaSQL upgrader manager hold the lock before it's assumed to have failed.
     * There is a tradeoff between giving the upgrade process enough time and recovering from a failed upgrade.
     * You may need to increase this value if your Kafka cluster is very busy.
     * <p>
     * However, since we have heartbeat messages, this time does not have to be too long.
     */
    @ConfigProperty(name = "registry.kafkasql.upgrade-lock-timeout", defaultValue = "10s")
    @Info(category = "store", description = "How long should KafkaSQL upgrader manager hold the lock before it's assumed to have failed. " +
            "There is a tradeoff between giving the upgrade process enough time and recovering from a failed upgrade. " +
            "You may need to increase this value if your Kafka cluster is very busy.", availableSince = "2.5.9.Final")
    @Getter
    Duration lockTimeout;

    @ConfigProperty(name = "registry.kafkasql.upgrade-test-mode", defaultValue = "false") // Keep undocumented!
    boolean testMode;

    @ConfigProperty(name = "registry.kafkasql.upgrade-test-init-delay", defaultValue = "0ms") // Keep undocumented!
    Duration testModeInitDelay;

    @Inject
    KafkaSqlSubmitter submitter;

    @Inject
    KafkaSqlStore sqlStore;

    @Inject
    Instance<KafkaSqlUpgrader> upgraders;

    @Inject
    Logger log;

    @Inject
    ManagedExecutor executor;

    @Inject
    ThreadContext threadContext;

    /**
     * Unique ID of this upgrader, generated on each Registry start.
     */
    private String localUpgraderUUID;

    private int currentVersion = BASE_KAFKASQL_TOPIC_VERSION;
    private int targetVersion = TARGET_KAFKASQL_TOPIC_VERSION;

    private Map<String, LockRecord> lockMap = new HashMap<>();

    private State state;
    private boolean retry;

    private long sequence;

    private volatile boolean localTryLocked;
    private Instant localTryLockedTimestamp;
    private volatile boolean upgrading;
    private int localTryLockCount = 0;

    private Instant initTimestamp;
    private Instant closeTimestamp;

    private Exception upgradeError;

    private WaitHeartbeatEmitter waitHeartbeatEmitter;


    /**
     * Initialize the upgrader. This method MUST be called before any other method,
     * and MUST be called only once.
     */
    public synchronized void init() {
        if (state != null) {
            throw new IllegalStateException("The init method MUST be called only once");
        }
        if (testMode) {
            log.warn("RUNNING IN TEST MODE");
            targetVersion = 99;
            try {
                Thread.sleep(testModeInitDelay.toMillis());
            } catch (InterruptedException ex) {
                // ignored
            }
        }

        initTimestamp = Instant.now();

        // Determine a UUID for this upgrader. It will serve both as an equivalent to the bootstrap message and to decide which node runs the upgrade.
        // We need to keep in mind that multiple nodes might start at the same time.
        // Upgrader runs only once on startup, so this can be set once.
        localUpgraderUUID = UUID.randomUUID().toString();

        waitHeartbeatEmitter = new WaitHeartbeatEmitter(scale(lockTimeout, 1.1f), submitter, log, threadContext);

        // Produce a bootstrap message to know when we are up-to-date with the topic. We don't know the version yet.
        submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_BOOTSTRAP, localUpgraderUUID, null));

        switchState(State.WAIT_FOR_BOOTSTRAP);
    }


    public synchronized void read(Instant currentTimestamp, MessageKey key, MessageValue value) {
        if (state == null) {
            throw new IllegalStateException("The init method MUST be called first");
        }
        // Read the topic data to determine:
        // - What is the current version
        // - If we are allowed to run the upgrade

        // Also send the messages to upgraders. We don't know which ones we will need yet,
        // so this adds a bit of overhead.
        if (state != State.CLOSED) {

            if (state != State.LOCKED && state != State.FAILED && state != State.CLOSING) {
                upgraders.forEach(u -> u.read(key, value));
            }

            if (key instanceof UpgraderKey) {
                // Update our lock map
                var upgraderValue = (UpgraderValue) value;
                updateLockMap(currentTimestamp, upgraderValue);
            }
        }

        retry = true;
        while (retry) {
            retry = false; // We may need to process the same message again after switching state.
            switch (state) {

                // This state represents the initial consuming of the topic to get the up-to-date information,
                // including the current version and activity of any other nodes.
                case WAIT_FOR_BOOTSTRAP: {
                    if (key instanceof UpgraderKey) {
                        var upgraderValue = (UpgraderValue) value;
                        switch (upgraderValue.getAction()) {
                            case UPGRADE_BOOTSTRAP: {
                                // We can ignore these unless it's ours
                                if (localUpgraderUUID.equals(upgraderValue.getUpgraderUUID())) {
                                    // Found our bootstrap message and current version, check if anyone is holding the lock
                                    log.debug("Bootstrapped, waiting to upgrade.");
                                    // Try to detect potential timing issues
                                    var slip = Duration.between(initTimestamp, currentTimestamp).abs().toMillis();
                                    if (slip > scale(lockTimeout, 0.25f).toMillis()) {
                                        log.warn("We detected a significant time difference ({} ms) between a moment when a Kafka message is produced (local time), " +
                                                "and it's creation timestamp reported by Kafka at the moment it is consumed. If this causes issues during KafkaSQL storage upgrade, " +
                                                "consider increasing 'registry.kafkasql.upgrade-lock-timeout' config value (currently {} ms).", slip, lockTimeout);
                                    }
                                    switchState(State.WAIT);
                                }
                            }
                            break;
                            case UPGRADE_TRY_LOCK:
                            case UPGRADE_ABORT_AND_UNLOCK:
                            case UPGRADE_COMMIT_AND_UNLOCK:
                            case UPGRADE_LOCK_HEARTBEAT:
                            case UPGRADE_WAIT_HEARTBEAT:
                                break; // ignore, but we can check for unknown action types
                            default:
                                throw new RuntimeAssertionFailedException("Read an upgrader manager message with unsupported action type: " + upgraderValue.getAction());
                        }
                    }
                }
                break;

                // This state represents us waiting for acquiring our lock, or
                // waiting on other node trying to upgrade.
                case WAIT: {
                    var activeLock = computeActiveLock();
                    if (activeLock == null) {
                        log.debug("No active upgrader manager lock found.");
                        // Lock has been unlocked, check if the upgrade succeeded
                        if (currentVersion >= targetVersion) {
                            if (currentVersion != targetVersion) {
                                log.warn("Current storage version {} is later than the target version {}. " +
                                        "Running an older version of Registry after a storage upgrade might cause problems.", currentVersion, targetVersion);
                            }
                            log.info("KafkaSQL storage topic is up-to-date (version {}).", targetVersion);
                            switchState(State.CLOSING);
                        } else {
                            // Nobody tried to upgrade yet, or failed, we should try.
                            if (localTryLocked) {
                                // We have tried to lock, eventually it might be our turn to go, but we have to check for our own timeout
                                var now = Instant.now();
                                if (lockMap.get(localUpgraderUUID).latestLockTimestamp != null && now.isAfter(lockMap.get(localUpgraderUUID).latestLockTimestamp.plus(lockTimeout)) &&
                                        localTryLockedTimestamp != null && now.isAfter(localTryLockedTimestamp.plus(scale(lockTimeout, 1.5f)))) { // We need to prevent loop here, so we keep a local timestamp as well
                                    // Our own lock has timed out, we can try again
                                    localTryLocked = false;
                                    switchState(State.TRY_LOCK);
                                    log.warn("Our own lock attempt timed out.");
                                } else {
                                    log.debug("Waiting for us or somebody else to acquire the lock.");
                                    // We need to ensure that we receive some kind of message to be woken up.
                                    // This handles an edge situation when a node's own lock has timed out,
                                    // but other nodes have crashed and there is no-one to send a message to the topic.
                                    waitHeartbeatEmitter.runDelayedWaitHeartbeatOnce();
                                }
                            } else {
                                // This should only happen if we have not already run prepare for upgrade, or we didn't have enough time.
                                switchState(State.TRY_LOCK);
                            }
                        }
                    } else {
                        // Some upgrader manager still holds the lock,
                        // check if we are the one who got it.
                        if (localUpgraderUUID.equals(activeLock.upgraderUUID)) {
                            // We've got the lock, but we may have sent an unlock message
                            if (localTryLocked) {
                                // We got the lock, but first check if we have enough time.
                                var now = Instant.now();
                                if (now.isAfter(lockMap.get(localUpgraderUUID).latestLockTimestamp.plus(scale(lockTimeout, 0.5f)))) {
                                    // We should unlock and wait, then try again
                                    log.warn("We've got the lock but we don't have enough time ({} ms remaining). Unlocking.",
                                            Duration.between(lockMap.get(localUpgraderUUID).latestLockTimestamp, now).toMillis());
                                    submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_ABORT_AND_UNLOCK, localUpgraderUUID, targetVersion));
                                    localTryLocked = false;
                                    // No need to send heartbeat, since we're expecting to read the unlock message
                                } else {
                                    // We're ready to go!
                                    switchState(State.LOCKED);
                                }
                            } else {
                                // No need to send heartbeat, since we're expecting to read the unlock message
                                log.debug("Waiting for our unlock to propagate.");
                            }
                        } else {
                            log.debug("Another upgrader manager holds the lock (UUID = {}, latestTimestamp = {}). Waiting.", activeLock.upgraderUUID, activeLock.latestLockTimestamp);
                            // We need to ensure that we receive some kind of message to be woken up.
                            // This handles an edge situation when a node that is holding the lock blocks
                            // for a long time. The other nodes are waiting, but cannot act on a lock timeout
                            // because this method is not called.
                            waitHeartbeatEmitter.runDelayedWaitHeartbeatOnce();
                        }
                    }
                }
                break;

                // This state represent a situation when we don't hold the lock, but need to attempt locking.
                // (After we determine if an upgrade is needed, and if so, prepare upgraders.)
                case TRY_LOCK: {
                    if (localTryLockCount > 3) {
                        log.warn("Maximum number of locked attempts (3) reached. Stopping to allow Registry to start, probably without an upgrade.");
                        switchState(State.FAILED);
                    } else {
                        log.debug("Attempting to lock (UUID = {}).", localUpgraderUUID);
                        // Try to figure out if *we* should be the one to upgrade. Send a try lock message with a new version.
                        submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_TRY_LOCK, localUpgraderUUID, targetVersion));
                        localTryLocked = true;
                        localTryLockedTimestamp = Instant.now();
                        localTryLockCount++;
                        // And wait for others to contest. Who tries first wins!
                        switchState(State.WAIT);
                    }
                }
                break;

                // This state represent us holding the lock. We can upgrade.
                case LOCKED: {
                    if (!upgrading) {
                        log.info("Lock acquired (UUID = {}) to perform upgrade from version {} to version {}.", localUpgraderUUID, currentVersion, targetVersion);
                        upgrading = true;
                        executor.execute(() -> {
                            try {
                                var activeUpgraders = computeActiveUpgraders();
                                if (activeUpgraders.isEmpty()) {
                                    log.info("No upgraders found for this version.");
                                } else {
                                    log.info("Performing an upgrade with (in order): {} .", activeUpgraders.stream()
                                            .map(u -> u.getClass().getSimpleName()).collect(Collectors.joining(", ")));
                                    var lockHeartbeat = new UpgraderManagerHandle(submitter, localUpgraderUUID, log, lockTimeout, targetVersion);
                                    lockHeartbeat.heartbeat(); // Initialize heartbeat
                                    if (testMode) {
                                        try {
                                            var c = sqlStore.getContentEntityByContentId(2);
                                            log.debug("Content hash before: {}", c.contentHash);
                                            log.debug("Canonical content hash before: {}", c.canonicalHash);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    for (KafkaSqlUpgrader u : activeUpgraders) {
                                        u.upgrade(lockHeartbeat);
                                        // Check if we are out of time. We prefer retry to executing two upgrades at the same time.
                                        if (lockHeartbeat.isTimedOut()) {
                                            submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_ABORT_AND_UNLOCK, localUpgraderUUID, targetVersion));
                                            localTryLocked = false;
                                            switchState(State.WAIT);
                                            log.warn("Upgrader {} ran out of time (took {} ms). Make sure it sends heartbeat often enough.",
                                                    u.getClass().getSimpleName(), Duration.between(lockHeartbeat.lastHeartbeat, Instant.now()).toMillis());
                                            return;
                                        }
                                        lockHeartbeat.heartbeat();
                                    }
                                    if (testMode) {
                                        try {
                                            var c = sqlStore.getContentEntityByContentId(2);
                                            log.debug("Content hash after: {}", c.contentHash);
                                            log.debug("Canonical content hash after: {}", c.canonicalHash);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                                submitter.send(UpgraderKey.create(false), UpgraderValue.create(ActionType.UPGRADE_COMMIT_AND_UNLOCK, localUpgraderUUID, targetVersion));
                                log.info("Upgrade finished successfully!");
                                switchState(State.CLOSING);
                            } catch (Exception ex) {
                                upgradeError = ex;
                                switchState(State.FAILED);
                            } finally {
                                upgrading = false;
                            }
                        });
                    }
                }
                break;

                // This state represents that the upgrade process on *this* node was not successful.
                case FAILED: {
                    // Currently, we just log and close. We might stop the application in the future.
                    var suffix = "If you are starting multiple nodes, check if another one succeeded, otherwise we suggest a restart.";
                    if (upgradeError != null) {
                        log.error("Upgrade failed with an error. " + suffix, upgradeError);
                    } else {
                        log.warn("Upgrade failed. {}", suffix);
                    }
                    submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_ABORT_AND_UNLOCK, localUpgraderUUID, targetVersion));
                    switchState(State.CLOSING);
                }
                break;

                case CLOSING: {
                    // Close all upgraders
                    upgraders.stream().forEach(u -> {
                        try {
                            u.close();
                        } catch (Exception e) {
                            log.warn("Failed to close upgrader {} because of {}:{}", u.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
                        }
                    });
                    waitHeartbeatEmitter.close();
                    lockMap = null; // Release resources
                    closeTimestamp = Instant.now();
                    log.debug("Closed.");
                    switchState(State.CLOSED);
                }
                break;

                case CLOSED: {
                    // ignore
                }
                break;

                default:
                    throw new UnreachableCodeException();
            }
        }
        sequence++;
    }


    public synchronized boolean isClosed() {
        return state == State.CLOSED;
    }


    public synchronized Duration getBootstrapAndUpgradeDuration() {
        requireNonNull(initTimestamp);
        requireNonNull(closeTimestamp);
        return Duration.between(initTimestamp, closeTimestamp);
    }


    private synchronized void switchState(State target) {
        log.debug("State change: {} -> {}", state, target);
        state = target;
        retry = true;
    }


    private synchronized List<KafkaSqlUpgrader> computeActiveUpgraders() {
        var duplicates = new HashSet<Class<?>>();
        var list = new ArrayList<KafkaSqlUpgrader>();
        for (int v = currentVersion; v < targetVersion; v++) {
            for (KafkaSqlUpgrader upgrader : upgraders) {
                if (!duplicates.contains(upgrader.getClass()) && upgrader.supportsVersion(v)) {
                    duplicates.add(upgrader.getClass());
                    list.add(upgrader);
                }
            }
        }
        return list;
    }


    /**
     * Might return null.
     * WARNING: Always returns null if the topic is already up-to-date, make sure to check for this situation.
     */
    private LockRecord computeActiveLock() {
        // If the current version matches (or is later than) the target version, just return null
        if (currentVersion >= targetVersion) {
            if (currentVersion != targetVersion) {
                log.warn("Current storage version {} is later than the target version {}. " +
                        "Running an older version of Registry after a storage upgrade might cause problems.", currentVersion, targetVersion);
            }
            return null;
        }
        // Upgrader who holds the active lock must be the first one fulfilling the following:
        // - Target version must match
        // - Must be try locked
        // - Must not be timed out
        var r = lockMap.values().stream()
                .filter(rr -> rr.targetVersion != null &&
                        rr.targetVersion == targetVersion &&
                        rr.tryLocked &&
                        !rr.isTimedOut(Instant.now(), lockTimeout))
                .min(Comparator.comparingLong(rr -> rr.tryLockSequence));
        return r.orElse(null);
    }


    private void updateLockMap(Instant timestamp, UpgraderValue value) {
        if (value.getUpgraderUUID() == null) {
            return;
        }
        var r = lockMap.computeIfAbsent(value.getUpgraderUUID(), k -> {
            var rr = new LockRecord();
            rr.upgraderUUID = k;
            return rr;
        });
        switch (value.getAction()) {
            case UPGRADE_BOOTSTRAP:
            case UPGRADE_WAIT_HEARTBEAT:
                break; // ignore
            case UPGRADE_TRY_LOCK: {
                r.targetVersion = value.getVersion();
                r.tryLockSequence = sequence;
                r.tryLocked = true;
                r.latestLockTimestamp = timestamp;
            }
            break;
            case UPGRADE_ABORT_AND_UNLOCK: {
                r.tryLocked = false;
                r.latestLockTimestamp = timestamp;
            }
            break;
            case UPGRADE_COMMIT_AND_UNLOCK: {
                r.tryLocked = false;
                r.latestLockTimestamp = timestamp;
                // We can use this to find the latest version
                if (value.getVersion() > currentVersion) {
                    currentVersion = value.getVersion();
                }
            }
            break;
            case UPGRADE_LOCK_HEARTBEAT: {
                r.latestLockTimestamp = timestamp;
            }
            break;
            default:
                throw new RuntimeAssertionFailedException("Read an upgrader message with unsupported action type: " + value.getAction());
        }
    }


    private enum State {
        WAIT_FOR_BOOTSTRAP,
        WAIT,
        TRY_LOCK,
        LOCKED,
        FAILED,
        CLOSING,
        CLOSED
    }


    private static class LockRecord {
        // UUID of the upgrader
        String upgraderUUID;
        // Version the upgrader is trying to upgrade to
        Integer targetVersion;
        // Sequence # of the message when the try lock happened
        long tryLockSequence = -1;
        // Timestamp for when the last message happened while holding the lock
        Instant latestLockTimestamp;
        // Is the upgrader trying to lock?
        boolean tryLocked;


        private boolean isTimedOut(Instant now, Duration lockTimeout) {
            return latestLockTimestamp != null && now.isAfter(latestLockTimestamp.plus(lockTimeout));
        }
    }


    public static class UpgraderManagerHandle {

        private Instant lastHeartbeat;
        private final KafkaSqlSubmitter submitter;
        private final String localUpgraderUUID;
        private final Logger log;
        @Getter
        private final Duration lockTimeout;
        @Getter
        private final int targetVersion;


        private UpgraderManagerHandle(KafkaSqlSubmitter submitter, String localUpgraderUUID, Logger log, Duration lockTimeout, int targetVersion) {
            this.submitter = submitter;
            this.localUpgraderUUID = localUpgraderUUID;
            this.log = log;
            this.lockTimeout = lockTimeout;
            this.targetVersion = targetVersion;
        }


        /**
         * This method should be periodically called by upgraders, to ensure the upgrade lock does not time out.
         * Calling this method *does not* necessarily produce a heartbeat, so it should be called as often as needed (within reason).
         * <p>
         * WARNING: Make sure to stop calling the heartbeat in the event of unrecoverable failure,
         * to avoid holding the lock.
         */
        public synchronized void heartbeat() {
            var now = Instant.now();
            if (lastHeartbeat == null || now.isAfter(lastHeartbeat.plus(scale(lockTimeout, 0.35f)))) {
                log.debug("Sending lock heartbeat.");
                submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_LOCK_HEARTBEAT, localUpgraderUUID, null));
                lastHeartbeat = now;
            }
        }


        private synchronized boolean isTimedOut() {
            return Instant.now().isAfter(lastHeartbeat.plus(scale(lockTimeout, 0.85f)));
        }
    }


    private static class WaitHeartbeatEmitter implements AutoCloseable {

        private volatile Thread thread;
        private final Duration delay;
        private volatile Instant next;
        private final KafkaSqlSubmitter submitter;
        private final Logger log;
        private final ThreadContext threadContext;
        private volatile boolean stop = false;


        public WaitHeartbeatEmitter(Duration delay, KafkaSqlSubmitter submitter, Logger log, ThreadContext threadContext) {
            this.delay = delay;
            this.submitter = submitter;
            this.log = log;
            this.threadContext = threadContext;
        }


        public void runDelayedWaitHeartbeatOnce() {
            if (thread == null) {
                thread = new Thread(threadContext.contextualRunnable(() -> {
                    log.debug("WaitHeartbeatEmitter thread has started.");
                    while (!stop) {
                        try {
                            if (next != null) {
                                var now = Instant.now();
                                if (now.isAfter(next)) {
                                    log.debug("Sending wait heartbeat.");
                                    submitter.send(UpgraderKey.create(true), UpgraderValue.create(ActionType.UPGRADE_WAIT_HEARTBEAT, null, null));
                                    next = null; // Ensure that the wait heartbeat is sent only once per the method call
                                } else {
                                    Thread.sleep(Duration.between(now, next).abs().toMillis());
                                }
                            } else {
                                Thread.sleep(1000); // Minimum delay to avoid spinning
                            }
                        } catch (InterruptedException ex) {
                            // Stop?
                        }
                    }
                }));
                thread.start();
            }
            next = Instant.now().plus(delay);
        }


        @Override
        public void close() {
            if (thread != null) {
                stop = true;
                thread.interrupt();
                try {
                    thread.join(1000);
                    if (!thread.isAlive()) {
                        log.debug("WaitHeartbeatEmitter thread has stopped successfully.");
                    } else {
                        log.warn("WaitHeartbeatEmitter thread has failed to stop within 1000ms.");
                    }
                } catch (InterruptedException ex) {
                    log.warn("WaitHeartbeatEmitter::close() interrupted: {}", ex.getMessage());
                }
            }
        }
    }


    public static Duration scale(Duration original, float scale) {
        return Duration.ofMillis((long) (original.toMillis() * scale));
    }


    /* This is the current lock timeout schematic:
     *
     * Lock timeout:         |------------------------------|        100% =  10s (default)
     * Wait heartbeat:       |                              .  |     110% =  11s
     * Lock heartbeat:       |           |                  .         35% = 3.5s
     * Too late to upgrade:  |               |              .         50% =   5s
     * Upgrader timeout*:    |                          |   .         85% = 8.5s
     *
     * * This is the longest time upgrader can block without sending a heartbeat,
     *   assuming that the heartbeat Kafka message is stored within ~15% of lock timeout (1.5s by default).
     */
}
