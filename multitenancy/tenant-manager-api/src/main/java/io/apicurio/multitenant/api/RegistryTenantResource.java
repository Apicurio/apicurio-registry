package io.apicurio.multitenant.api;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.multitenant.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.datamodel.RegistryTenant;
import io.apicurio.multitenant.persistence.RegistryTenantRepository;
import io.apicurio.multitenant.persistence.TenantNotFoundException;
import io.apicurio.multitenant.persistence.dto.RegistryTenantDto;

@Path("/v1/tenants")
public class RegistryTenantResource {

    @Inject
    RegistryTenantRepository tenantsRepository;

    @Inject
    @ConfigProperty(name = "tenant.manager.auth.server.url")
    Optional<String> defaultAuthServerUrl;


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<RegistryTenant> listTenants() {
        return tenantsRepository.listAll()
                .stream()
                .map(RegistryTenantDto::toDatamodel)
                .collect(Collectors.toList());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTenant(NewRegistryTenantRequest tenantRequest) {

        required(tenantRequest.getOrganizationId(), "OrganizationId is mandatory");
        required(tenantRequest.getClientId(), "ClientId is mandatory");

        RegistryTenantDto tenant = new RegistryTenantDto();

        tenant.setTenantId(UUID.randomUUID().toString());

        tenant.setOrganizationId(tenantRequest.getOrganizationId());
        tenant.setAuthClientId(tenantRequest.getClientId());

        if (tenantRequest.getAuthServerUrl() == null && defaultAuthServerUrl.isPresent()) {
            tenant.setAuthServerUrl(defaultAuthServerUrl.get());
        } else if (tenantRequest.getAuthServerUrl() != null) {
            tenant.setAuthServerUrl(tenantRequest.getAuthServerUrl());
        } else {
            throw new BadRequestException("No default authServerUrl, authServerUrl is mandatory");
        }

        tenant.setCreatedOn(new Date());
        tenant.setCreatedBy(null); //TODO extract user from auth details
        tenant.setDeploymentFlavor(tenantRequest.getDeploymentFlavor());
        tenant.setStatus("READY"); //TODO

        tenantsRepository.save(tenant);

        return Response.status(Status.CREATED).entity(tenant.toDatamodel()).build();
    }

    @GET
    @Path("{tenantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public RegistryTenant getTenant(@PathParam("tenantId") String tenantId) {
        return tenantsRepository.findByTenantId(tenantId)
                .map(RegistryTenantDto::toDatamodel)
                .orElseThrow(() -> TenantNotFoundException.create(tenantId));
    }

    @DELETE
    @Path("{tenantId}")
    public Response deleteTenant(@PathParam("tenantId") String tenantId) {

        tenantsRepository.delete(tenantId);

        return Response.noContent().build();
    }

    private void required(String parameter, String message) {
        if (parameter == null || parameter.isEmpty()) {
            throw new BadRequestException(message);
        }
    }

}
