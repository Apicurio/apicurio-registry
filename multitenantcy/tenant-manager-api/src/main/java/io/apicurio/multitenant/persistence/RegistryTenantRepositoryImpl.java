package io.apicurio.multitenant.persistence;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import io.apicurio.multitenant.persistence.dto.RegistryTenantDto;
import io.apicurio.multitenant.persistence.hibernate.RegistryTenantPanacheRepository;

@ApplicationScoped
public class RegistryTenantRepositoryImpl implements RegistryTenantRepository {

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
