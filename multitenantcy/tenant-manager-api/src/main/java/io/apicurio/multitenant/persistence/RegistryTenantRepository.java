package io.apicurio.multitenant.persistence;

import java.util.List;
import java.util.Optional;

import io.apicurio.multitenant.persistence.dto.RegistryTenantDto;


public interface RegistryTenantRepository {

    void save(RegistryTenantDto dto);

    Optional<RegistryTenantDto> findByTenantId(String tenantId);

    List<RegistryTenantDto> listAll();

    void delete(String tenantId);

}
