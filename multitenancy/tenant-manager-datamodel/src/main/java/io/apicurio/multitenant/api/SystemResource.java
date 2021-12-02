package io.apicurio.multitenant.api;

import io.apicurio.multitenant.api.datamodel.SystemInfo;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/api/v1/system")
public interface SystemResource {
  /**
   * This operation retrieves information about the running registry system, such as the version
   * of the software and when it was built.
   */
  @Path("/info")
  @GET
  @Produces("application/json")
  SystemInfo getSystemInfo();
}
