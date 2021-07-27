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

import io.apicurio.multitenant.api.beans.RegistryTenantList;
import io.apicurio.multitenant.api.beans.SortBy;
import io.apicurio.multitenant.api.beans.SortOrder;
import io.apicurio.multitenant.api.beans.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.OptionalBean;
import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;

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

    @PostConstruct
    void init() {
        if (!properties.isMultitenancyEnabled()) {
            return;
        }
        // Start with a random stagger, 1-15 minutes, inclusive.
        int stagger = new Random().nextInt(15) + 1;
        log.debug("Staggering tenant reaper job by {} minutes", stagger);
        next = Instant.now().plus(Duration.ofSeconds(stagger * 60L));
    }

    /**
     * Minimal granularity is 1 minute.
     */
    @Scheduled(every = "60s")
    void run() {
        if (!properties.isMultitenancyEnabled()) {
            return;
        }
        final Instant now = Instant.now();
        if (now.isAfter(next)) {
            try {
                log.debug("Running tenant reaper job at {}", now);
                reap();
            } catch (Exception ex) {
                log.error("Exception thrown when running tenant reaper job", ex);
            } finally {
                next = now.plus(properties.getReaperPeriod());
                log.debug("Running next tenant reaper job at around {}", next);
            }
        }
    }

    void reap() {
        List<RegistryTenant> page;
        do {
            RegistryTenantList tenants = tenantManagerClient.get().listTenants(
                TenantStatusValue.TO_BE_DELETED,
                0, 50, SortOrder.asc, SortBy.tenantId);
            page = tenants.getItems();
            for (RegistryTenant tenant : page) {
                final String tenantId = tenant.getTenantId();
                try {
                    log.debug("Deleting tenant '{}' data", tenantId);
                    tcl.invalidateTenantInCache(tenantId);
                    // TODO Refactor, document and improve context handling.
                    tctx.setContext(tcl.loadContext(tenantId));
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
        } while (!page.isEmpty());
    }

    void setNext(Instant next) {
        this.next = next;
    }
}
