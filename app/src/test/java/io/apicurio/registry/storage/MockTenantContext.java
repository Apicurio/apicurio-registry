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
package io.apicurio.registry.storage;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Alternative;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.mt.TenantContextImpl;
import io.quarkus.runtime.StartupEvent;

/**
 * @author Fabian Martinez
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockTenantContext extends TenantContextImpl {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    void init(@Observes StartupEvent ev) {
        log.info("Using Mock TenantContext");
    }

    @Override
    public void tenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void clearTenantId() {
        this.tenantId = DEFAULT_TENANT_ID;
    }

}
