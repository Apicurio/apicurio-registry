package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.config.Config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class UpdateNotifier {

    private static final Logger log = Logger.getLogger(UpdateNotifier.class);
    private static final Duration CHECK_INTERVAL = Duration.ofDays(1);

    /**
     * How long the next check is deferred after a single failed one. Much shorter than
     * {@link #CHECK_INTERVAL} so a transient failure — a dropped network, a proxy, a timeout —
     * recovers quickly, while still being long enough that a command run in a loop does not pay for
     * a network attempt every time.
     * <p>
     * The backoff is kept in the CLI config rather than expressed with MicroProfile Fault Tolerance,
     * because each command is a separate short-lived process: the state that has to survive is the
     * gap between two invocations, not retries within one.
     */
    static final Duration FAILURE_RETRY_INTERVAL = Duration.ofMinutes(15);

    /**
     * Upper bound on the recorded consecutive-failure count. The backoff saturates at
     * {@link #CHECK_INTERVAL} well before this, so the cap exists only to keep the stored value and
     * the doubling below bounded however long a failure persists.
     */
    static final int MAX_FAILURE_COUNT = 30;

    private static final Set<String> SKIP_COMMANDS = Set.of("install", "update", "version", "config");

    @Inject
    Config config;

    @Inject
    Update update;

    public void checkAndNotify(String commandName) {
        log.debugf("Update check hook: command=%s", commandName);
        try {
            if (SKIP_COMMANDS.contains(commandName)) {
                log.debugf("Update check skipped: command '%s' is in skip list", commandName);
                return;
            }
            if (!shouldCheck()) {
                return;
            }

            var currentVersion = config.getCliVersion();
            config.getStdErr().print("Checking for updates...\n");
            log.debugf("Checking for updates (current version: %s)", currentVersion);
            var result = update.checkForUpdates(currentVersion);

            if (result.hasUpdates()) {
                log.debugf("Updates available: %s", result.candidates());
                printNotification(result);
            } else {
                config.getStdErr().print("No updates available.\n");
            }
        } catch (Exception e) {
            log.debugf("Update check failed: %s", e.getMessage());
            config.getStdErr().print("Warning: Could not check for updates. Run 'acr update --check' to retry.\n");
        }
    }

    private boolean shouldCheck() {
        try {
            var configModel = config.read();
            var props = configModel.getConfig();

            if ("false".equalsIgnoreCase(props.get("update.check-enabled"))) {
                log.debugf("Update check skipped: update.check-enabled=false");
                return false;
            }

            var postponedUntil = props.get("internal.update.postponed-until");
            if (postponedUntil != null) {
                var until = Instant.parse(postponedUntil);
                if (Instant.now().isBefore(until)) {
                    log.debugf("Update check skipped: postponed until %s", until);
                    return false;
                }
            }

            if (isBackingOffAfterFailure(props)) {
                return false;
            }

            var lastCheck = props.get("internal.update.last-check");
            if (lastCheck != null) {
                var last = Instant.parse(lastCheck);
                var elapsed = Duration.between(last, Instant.now());
                if (elapsed.compareTo(CHECK_INTERVAL) < 0) {
                    log.debugf("Update check skipped: last check was %s ago (interval: %s)", elapsed, CHECK_INTERVAL);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.debugf("Update check skipped: could not read config: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Reports whether the previous check failed recently enough that the next one should be skipped.
     * <p>
     * Without this, a check that fails is never recorded, so the auto-check hook retries it on every
     * single command: a persistent problem (an unreachable repository, no network, a proxy) costs a
     * network attempt and two lines of output every time the user runs anything.
     */
    private boolean isBackingOffAfterFailure(Map<String, String> props) {
        var lastFailure = props.get("internal.update.last-failure");
        if (lastFailure == null) {
            return false;
        }
        Instant failedAt;
        try {
            failedAt = Instant.parse(lastFailure);
        } catch (DateTimeParseException ex) {
            // Parsed defensively on purpose: an unreadable marker must not disable update checks
            // permanently, which is what letting this propagate to the caller's catch would do.
            log.debugf("Ignoring unparseable update failure timestamp: %s", lastFailure);
            return false;
        }
        var retryAt = failedAt.plus(failureBackoff(parseFailureCount(props.get("internal.update.failure-count"))));
        if (Instant.now().isBefore(retryAt)) {
            log.debugf("Update check skipped: previous check failed, next attempt at %s", retryAt);
            return true;
        }
        return false;
    }

    /**
     * Returns how long to wait after {@code failureCount} consecutive failed checks, doubling
     * {@link #FAILURE_RETRY_INTERVAL} for each one and never exceeding {@link #CHECK_INTERVAL}, so a
     * repeatedly failing check is never attempted more often than a healthy one.
     *
     * @param failureCount consecutive failures recorded so far; zero means no backoff
     */
    static Duration failureBackoff(int failureCount) {
        if (failureCount <= 0) {
            return Duration.ZERO;
        }
        var doublings = Math.min(failureCount - 1, MAX_FAILURE_COUNT);
        var backoff = FAILURE_RETRY_INTERVAL.multipliedBy(1L << doublings);
        return backoff.compareTo(CHECK_INTERVAL) > 0 ? CHECK_INTERVAL : backoff;
    }

    /**
     * Reads the recorded consecutive-failure count, clamped to {@code [0, }{@link #MAX_FAILURE_COUNT}
     * {@code ]}. The value lives in a user-editable config file, so anything absent, malformed or out
     * of range is treated as "no failures recorded" rather than being allowed to fail the check.
     */
    static int parseFailureCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            var count = Integer.parseInt(value.trim());
            return Math.max(0, Math.min(count, MAX_FAILURE_COUNT));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void printNotification(UpdateCheckResult result) {
        var productName = getProductName();
        var sb = new StringBuilder();
        sb.append("\n");
        if (result.isAmbiguous()) {
            sb.append("New versions of ").append(productName).append(" are available:\n");
        } else {
            sb.append("A new version of ").append(productName).append(" is available:\n");
        }
        result.formatMessage(sb);
        sb.append("Run 'acr update --postpone' to postpone for 5 days.\n");
        config.getStdErr().print(sb.toString());
    }

    private String getProductName() {
        try {
            var name = config.read().getConfig().get("internal.branding.product-name");
            return name != null ? name : "Apicurio Registry CLI";
        } catch (Exception e) {
            return "Apicurio Registry CLI";
        }
    }
}
