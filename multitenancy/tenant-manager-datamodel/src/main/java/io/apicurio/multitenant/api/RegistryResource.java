package io.apicurio.multitenant.api;

import io.apicurio.multitenant.api.datamodel.RegistryDeploymentInfo;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/api/v1/registry")
public interface RegistryResource {
  @GET
  @Produces("application/json")
  RegistryDeploymentInfo getRegistryInfo();
}
