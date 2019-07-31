package io.apicurio.registry.rest;

import io.apicurio.registry.rest.beans.Rule;
import java.lang.String;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/rules")
public interface RulesResource {
  @GET
  @Produces("application/json")
  List<Rule> listGlobalRules();

  @POST
  @Consumes("application/json")
  void createGlobalRule(Rule data);

  @DELETE
  void deleteAllGlobalRules();

  @Path("/{rule}")
  @GET
  @Produces("application/json")
  Rule getGlobalRuleConfig(@PathParam("rule") String rule);

  @Path("/{rule}")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  Rule updateGlobalRuleConfig(@PathParam("rule") String rule, Rule data);

  @Path("/{rule}")
  @DELETE
  void deleteGlobalRule(@PathParam("rule") String rule);
}
