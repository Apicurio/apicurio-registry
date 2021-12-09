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

package io.apicurio.multitenant.metrics;

import static io.apicurio.multitenant.metrics.MetricsConstants.REST_REQUESTS_TIMER_DESCRIPTION;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.storage.RegistryTenantStorage;
import io.apicurio.multitenant.storage.dto.RegistryTenantDto;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Startup;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
@Startup
public class UsageMetrics {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    MeterRegistry metrics;

    @Inject
    RegistryTenantStorage tenantsRepository;

    @Inject
    @ConfigProperty(name = "tenant-manager.metrics.usage-statistics.cache-expiration-period-seconds")
    Integer expirationPeriodSeconds;

    private Duration expirationPeriod;

    private Instant nextExpiration;

    private Map<String, Long> tenantsCountByStatus;

    private Timer tenantsToBeDeletedTimer;

    @PostConstruct
    synchronized void init() {
        expirationPeriod = Duration.ofSeconds(expirationPeriodSeconds);

        int stagger = 0;
        // Only stagger if the expiration period is at least 1 minute (testing support).
        if (expirationPeriod.compareTo(Duration.ofMinutes(1)) >= 0) {
            stagger = new Random().nextInt(expirationPeriodSeconds) + 1;
            log.debug("Staggering usage metrics cache expiration by {} seconds", stagger);
        }
        nextExpiration = Instant.now().plus(Duration.ofSeconds(stagger));

        //tenants count by status
        for (TenantStatusValue status : TenantStatusValue.values()) {
            Gauge.builder(MetricsConstants.USAGE_TENANTS, () -> {
                Arc.initialize();
                var ctx = Arc.container().requestContext();
                ctx.activate();
                try {
                    return getTenantsCountByStatus().get(status.value());
                } finally {
                    ctx.deactivate();
                }
            })
            .tags(Tags.of(MetricsConstants.TAG_USAGE_TENANTS_STATUS, status.value()))
            .register(metrics);
        }

        //time tenants are in TO_BE_DELETED status
        tenantsToBeDeletedTimer = Timer
                .builder(MetricsConstants.USAGE_DELETING_TENANTS)
                .description(REST_REQUESTS_TIMER_DESCRIPTION)
                .register(metrics);
    }

    public void tenantStatusChanged(RegistryTenantDto tenant) {
        if (TenantStatusValue.fromValue(tenant.getStatus()) == TenantStatusValue.DELETED) {

            if (tenant.getModifiedOn() == null) {
                log.warn("tenant {} has null modifiedOn", tenant.getTenantId());
                return;
            }

            Instant toBeDeletedStart = tenant.getModifiedOn().toInstant();

            Duration toBeDeletedDuration = Duration.between(toBeDeletedStart, Instant.now());

            tenantsToBeDeletedTimer.record(toBeDeletedDuration);

        }
    }

    private synchronized Map<String, Long> getTenantsCountByStatus() {
        boolean expired = Instant.now().isAfter(nextExpiration);
        if (tenantsCountByStatus == null || expired) {
            tenantsCountByStatus = tenantsRepository.getTenantsCountByStatus();
            if (expired) {
                nextExpiration = Instant.now().plus(expirationPeriod);
            }
        }
        return tenantsCountByStatus;
    }
}
