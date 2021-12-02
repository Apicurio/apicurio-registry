package io.apicurio.multitenant.api;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.api.datamodel.RegistryTenantList;
import io.apicurio.multitenant.api.datamodel.SortBy;
import io.apicurio.multitenant.api.datamodel.SortOrder;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/api/v1/tenants")
public interface TenantsResource {
  /**
   * Gets the details of a single instance of a `Tenant`.
   */
  @Path("/{tenantId}")
  @GET
  @Produces("application/json")
  RegistryTenant getTenant(@PathParam("tenantId") String tenantId);

  /**
   * Updates the name, description, and resources for a tenant.
   */
  @Path("/{tenantId}")
  @PUT
  @Consumes("application/json")
  void updateTenant(@PathParam("tenantId") String tenantId, UpdateRegistryTenantRequest data);

  /**
   * Marks an existing `Tenant` to be deleted.
   */
  @Path("/{tenantId}")
  @DELETE
  void deleteTenant(@PathParam("tenantId") String tenantId);

  /**
   * Gets a list of `RegistryTenant` entities according to the query parameters set.
   */
  @GET
  @Produces("application/json")
  RegistryTenantList getTenants(@QueryParam("status") String status,
      @QueryParam("offset") @Min(0) Integer offset, @QueryParam("limit") @Min(1) @Max(500) Integer limit,
      @QueryParam("order") SortOrder order, @QueryParam("orderby") SortBy orderby);

  /**
   * Creates a new instance of a `Tenant`.
   */
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  Response createTenant(NewRegistryTenantRequest data);
}
