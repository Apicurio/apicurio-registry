/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.mt;

import io.apicurio.common.apps.config.Info;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenantList;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.OptionalBean;
import io.quarkus.scheduler.Scheduled;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically cleanup data of tenants marked as deleted.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class TenantReaper {

    @Inject
    Logger log;

    @Inject
    MultitenancyProperties properties;

    @Inject
    TenantMetadataService tenantService;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    TenantContextLoader tcl;

    @Inject
    TenantContext tctx;

    @Inject
    OptionalBean<TenantManagerClient> tenantManagerClient;

    Instant next;

    @ConfigProperty(name = "registry.multitenancy.reaper.max-tenants-reaped", defaultValue = "100")
    @Info(category = "mt", description = "Multitenancy reaper max tenants reaped", availableSince = "2.1.0.Final")
    int maxTenantsReaped;

    @PostConstruct
    void init() {
        if (!properties.isMultitenancyEnabled()) {
            return;
        }
        int stagger = 0;
        // Only stagger if the reaper period is at least 1 minute (testing support).
        if (properties.getReaperPeriod().compareTo(Duration.ofSeconds(60)) >= 0) {
            // Start with a random stagger, 1-30 minutes, inclusive.
            stagger = new Random().nextInt(30) + 1;
            log.debug("Staggering tenant reaper job by {} minutes", stagger);
        }
        next = Instant.now().plus(Duration.ofSeconds(stagger * 60L));
    }

    /**
     * Minimal granularity is 1 minute.
     */
    @Scheduled(concurrentExecution = SKIP, every = "{registry.multitenancy.reaper.every}")
    void run() {
        if (!properties.isMultitenancyEnabled()) {
            return;
        }
        final Instant now = Instant.now();
        if (now.isAfter(next)) {
            try {
                log.debug("Running tenant reaper job at {}", now);
                reap();
                // Only force cache invalidation if the reaper period is less than 1 minute (testing support).
                if (properties.getReaperPeriod().compareTo(Duration.ofSeconds(60)) < 0) {
                    tcl.invalidateTenantCache();
                }
            } catch (Exception ex) {
                log.error("Exception thrown when running tenant reaper job", ex);
            } finally {
                next = now.plus(properties.getReaperPeriod());
                log.debug("Running next tenant reaper job at around {}", next);
            }
        }
    }

    /**
     * Query the tenant manager for the list of tenants in the "to-be-deleted" state.  Those tenants
     * must be "reaped", which simply means we must delete all of their user data (artifacts, global
     * rule configuration, etc).  This method is invoked by the scheduler and works by making an API
     * call to the tenant manager and iterating through the results.
     *
     * Note that a single invocation of reap() will reap a maximum of MAX_TENANTS_PROCESSED.  If there
     * are more tenants that need reaping, they will be processed the next time the schedule warrants it.
     * This is a defensive approach to ensure that the while loop is always bounded.
     */
    void reap() {
        List<ApicurioTenant> page;
        int tenantsProcessed = 0;
        do {
            ApicurioTenantList tenants = tenantManagerClient.get().listTenants(
                TenantStatusValue.TO_BE_DELETED,
                0, 10, SortOrder.asc, SortBy.tenantId);
            page = tenants.getItems();
            for (ApicurioTenant tenant : page) {
                final String tenantId = tenant.getTenantId();
                try {
                    log.debug("Deleting tenant '{}' data", tenantId);
                    tcl.invalidateTenantInCache(tenantId);
                    // TODO Refactor, document and improve context handling.
                    tctx.setContext(tcl.loadBatchJobContext(tenantId));
                    // Safety check
                    if (tenant.getStatus() != TenantStatusValue.TO_BE_DELETED || !tenantId.equals(tctx.tenantId())) {
                        log.debug("Safety: tenant.getStatus() = {}, tenantId = {}, ctx.tenantId() = {}",
                            tenant.getStatus(), tenantId, tctx.tenantId());
                        throw new IllegalStateException("Safety check failed when attempting to delete tenant data.");
                    }
                    storage.deleteAllUserData();
                    tenantService.markTenantAsDeleted(tenantId);
                    tcl.invalidateTenantInCache(tenantId);
                } catch (Exception ex) {
                    log.warn("Exception thrown when reaping tenant '" + tenantId + "'", ex);
                    // Just ignore, will retry on next cycle
                }
            }
            tenantsProcessed += page.size();
        } while (!page.isEmpty() && tenantsProcessed < maxTenantsReaped);
    }
}
