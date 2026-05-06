package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.config.Config;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class UpdateNotifier {

    private static final Logger log = Logger.getLogger(UpdateNotifier.class);
    private static final Duration CHECK_INTERVAL = Duration.ofDays(1);
    private static final int CHECK_TIMEOUT_SECONDS = 5;

    private static final Set<String> SKIP_COMMANDS = Set.of("install", "update", "version", "config");

    @Inject
    Config config;

    @Inject
    Update update;

    public void checkAndNotify(String commandName) {
        try {
            if (SKIP_COMMANDS.contains(commandName)) {
                log.debugf("Update check skipped: command '%s' is in skip list", commandName);
                return;
            }
            if (!shouldCheck()) {
                return;
            }

            var versionStr = ConfigProvider.getConfig().getValue("version", String.class);
            var currentVersion = CliVersion.parse(versionStr);
            if (currentVersion == null) {
                log.debugf("Update check skipped: could not parse current version '%s'", versionStr);
                return;
            }

            log.debugf("Checking for updates (current version: %s)", currentVersion);
            var future = CompletableFuture.supplyAsync(() -> update.checkForUpdates(currentVersion));
            var result = future.get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            recordCheckTimestamp();

            if (result.hasUpdates()) {
                log.debugf("Updates available: %s", result.candidates());
                printNotification(result);
            } else {
                log.debugf("No updates available");
            }
        } catch (Exception e) {
            log.debugf("Update check failed: %s", e.getMessage());
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

    private void recordCheckTimestamp() {
        try {
            var configModel = config.read();
            configModel.getConfig().put("internal.update.last-check", Instant.now().toString());
            config.write(configModel);
        } catch (Exception e) {
            log.debugf("Could not record update check timestamp: %s", e.getMessage());
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
