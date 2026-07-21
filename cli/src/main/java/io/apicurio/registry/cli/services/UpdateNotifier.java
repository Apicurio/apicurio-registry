package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.config.ConfigProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class UpdateNotifier {

    private static final Logger log = Logger.getLogger(UpdateNotifier.class);
    private static final Duration CHECK_INTERVAL = Duration.ofDays(1);

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
            var props = config.read().getConfig();

            if ("false".equalsIgnoreCase(props.get(ConfigProperties.UPDATE_CHECK_ENABLED))) {
                log.debugf("Update check skipped: update.check-enabled=false");
                return false;
            }

            var postponedUntil = props.get(ConfigProperties.INTERNAL_UPDATE_POSTPONED_UNTIL);
            if (postponedUntil != null) {
                var until = Instant.parse(postponedUntil);
                if (Instant.now().isBefore(until)) {
                    log.debugf("Update check skipped: postponed until %s", until);
                    return false;
                }
            }

            var lastCheck = props.get(ConfigProperties.INTERNAL_UPDATE_LAST_CHECK);
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
            var name = config.getProperty(ConfigProperties.INTERNAL_BRANDING_PRODUCT_NAME);
            return name != null ? name : "Apicurio Registry CLI";
        } catch (Exception e) {
            return "Apicurio Registry CLI";
        }
    }
}
