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
package io.apicurio.registry.mt.metadata;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.types.Current;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantMetadataServiceProducer {

    private static Logger log = LoggerFactory.getLogger(TenantMetadataServiceProducer.class);

    @Inject
    Instance<TenantMedataServiceProvider> provider;

    @Produces
    @ApplicationScoped
    @Current
    public TenantMetadataService realImpl() {

        if (provider.isResolvable()) {
            TenantMetadataService svc = provider.get().metadataService();
            log.info(String.format("Using TenantMetadataService: %s", svc.getClass().getName()));
            return svc;
        }

        return new NoopTenantMetadataService();

    }

}
