package io.apicurio.multitenant.persistence.hibernate;

import javax.enterprise.context.ApplicationScoped;
import io.apicurio.multitenant.persistence.dto.RegistryTenantDto;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

@ApplicationScoped
public class RegistryTenantPanacheRepository implements PanacheRepository<RegistryTenantDto> {

}