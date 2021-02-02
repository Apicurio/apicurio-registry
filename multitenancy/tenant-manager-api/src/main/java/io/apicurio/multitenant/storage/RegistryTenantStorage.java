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

import io.apicurio.multitenant.storage.dto.RegistryTenantDto;

/**
 * @author Fabian Martinez
 */
public interface RegistryTenantStorage {

    void save(RegistryTenantDto dto);

    Optional<RegistryTenantDto> findByTenantId(String tenantId);

    List<RegistryTenantDto> listAll();

    void delete(String tenantId);

}
