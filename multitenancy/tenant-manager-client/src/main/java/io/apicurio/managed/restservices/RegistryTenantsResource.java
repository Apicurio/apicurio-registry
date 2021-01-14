package io.apicurio.managed.restservices;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.apicurio.managed.dto.RegistryTenant;
import io.apicurio.managed.persistence.RegistryTenantsRepository;

@Path("tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistryTenantsResource {
    
    private RegistryTenantsRepository tenantsRepository;

    @Inject
    public RegistryTenantsResource(RegistryTenantsRepository tenantsRepository) {
        this.tenantsRepository = tenantsRepository;
    }

    @GET
    public List<String> listTenants() {
        return tenantsRepository.streamAll()
            .map(RegistryTenant::getTenantId)
            .collect(Collectors.toList());
    }

    @POST
    public Response createTenant() {

        //TODO

        return Response.ok().build();
    }

}
