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
package io.apicurio.multitenant.storage;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import io.apicurio.multitenant.storage.dto.RegistryTenantDto;
import io.apicurio.multitenant.storage.hibernate.RegistryTenantPanacheRepository;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryTenantStorageImpl implements RegistryTenantStorage {

    @Inject
    RegistryTenantPanacheRepository repo;

    @Override
    @Transactional
    public void save(RegistryTenantDto dto) {
        repo.persist(dto);
    }

    @Override
    public Optional<RegistryTenantDto> findByTenantId(String tenantId) {
        return repo.find("tenantId", tenantId).singleResultOptional();
    }

    @Override
    public List<RegistryTenantDto> listAll() {
        return repo.listAll();
    }

    @Override
    @Transactional
    public void delete(String tenantId) {
        RegistryTenantDto dto = findByTenantId(tenantId)
            .orElseThrow(() -> TenantNotFoundException.create(tenantId));
        repo.delete(dto);
    }

}
