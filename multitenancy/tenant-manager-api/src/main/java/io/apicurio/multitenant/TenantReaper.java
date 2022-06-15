package io.apicurio.multitenant;

import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.storage.RegistryTenantStorage;
import io.apicurio.multitenant.storage.dto.RegistryTenantDto;
import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class TenantReaper {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    RegistryTenantStorage storage;

    @Scheduled(concurrentExecution = SKIP, every = "{tenant-manager.tenant-reaper.period-expression}")
    void reap() {
        try {
            log.debug("Tenant Reaper executing");
            var deletedCount = 0;
            var tenants = storage.getTenantsByStatus(TenantStatusValue.DELETED);
            for (RegistryTenantDto tenant : tenants) {
                try {
                    storage.delete(tenant.getTenantId());
                    deletedCount++;
                } catch (Exception ex) {
                    log.error("Could not delete tenant marked with DELETED status. Tenant = " + tenant, ex);
                }
            }
            if (deletedCount > 0) {
                log.info("Tenant Reaper deleted {} tenants marked with DELETED status.", deletedCount);
            }
        } catch (Exception ex) {
            log.error("Could not execute Tenant Reaper", ex);
        }
    }
}
