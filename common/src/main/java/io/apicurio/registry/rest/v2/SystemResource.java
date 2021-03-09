package io.apicurio.registry.rest.v2;

import io.apicurio.registry.rest.v2.beans.SystemInfo;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/registry/v2/system")
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
