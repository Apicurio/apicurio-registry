package io.apicurio.multitenant;

import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.storage.RegistryTenantStorage;
import io.apicurio.multitenant.storage.dto.RegistryTenantDto;
import io.quarkus.scheduler.Scheduled;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class TenantReaper {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    RegistryTenantStorage storage;

    @ConfigProperty(name = "tenant-manager.tenant-reaper.max-tenants-reaped", defaultValue = "100")
    int maxTenantsReaped;

    @ConfigProperty(name = "tenant-manager.tenant-reaper.period-seconds", defaultValue = "10800") // 3 * 60 * 60 = 3 hours
    int reaperPeriodSeconds;

    private Duration reaperPeriod;

    private Instant next;

    private Duration getReaperPeriod() {
        if (reaperPeriod == null) {
            reaperPeriod = Duration.ofSeconds(reaperPeriodSeconds);
        }
        return reaperPeriod;
    }

    @PostConstruct
    void init() {
        var stagger = Duration.ZERO;
        // Only stagger if the reaper period is at least 1 minute (testing support).
        if (getReaperPeriod().compareTo(Duration.ofMinutes(1)) >= 0) {
            // Start with a random stagger, 1-30 minutes, inclusive.
            stagger = Duration.ofMinutes(new Random().nextInt(30) + 1L); // TODO Reuse RNG in multiple places
            log.info("Staggering tenant manager reaper job by {}", stagger);
        }
        next = Instant.now().plus(stagger);
    }

    @Scheduled(concurrentExecution = SKIP, every = "{tenant-manager.tenant-reaper.every}")
    void run() {
        final var now = Instant.now();
        if (now.isAfter(next)) {
            try {
                log.info("Running tenant manager reaper job at {}", now);
                reap();
                log.info("Tenant manager reaper job finished successfully");
            } catch (Exception ex) {
                log.error("Exception thrown when running tenant manager reaper job", ex);
            } finally {
                next = now.plus(getReaperPeriod());
                log.info("Tenant manager reaper job finished in {}", Duration.between(Instant.now(), now));
                log.info("Running next tenant reaper job at around {}", next);
            }
        }
    }

    private synchronized void reap() {
        var deletedCount = 0;
        var processedCount = 0; // This also counts failed deletions
        List<RegistryTenantDto> page;
        do {
            log.info("Getting a page of tenants to delete");
            page = storage.getTenantsByStatus(TenantStatusValue.DELETED, 10);
            log.info("A page of tenants to delete: {}", page);
            for (RegistryTenantDto tenant : page) {
                try {
                    log.info("Deleting tenant {}", tenant.getTenantId());
                    storage.delete(tenant.getTenantId());
                    deletedCount++;
                } catch (Exception ex) {
                    log.error("Tenant manager reaper could not delete tenant " + tenant, ex);
                }
            }
            processedCount += page.size();
        } while (!page.isEmpty() && processedCount < maxTenantsReaped);
        log.info("Tenant manager reaper deleted {} tenants marked with DELETED status.", deletedCount);
    }
}
