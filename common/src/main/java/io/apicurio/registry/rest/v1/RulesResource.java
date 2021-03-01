package io.apicurio.registry.rest.v1;

import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.types.RuleType;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/registry/v1/rules")
public interface RulesResource {
  /**
   * Returns information about the named globally configured rule.
   *
   * This operation can fail for the following reasons:
   *
   * * Invalid rule name/type (HTTP error `400`)
   * * No rule with name/type `rule` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{rule}")
  @GET
  @Produces("application/json")
  Rule getGlobalRuleConfig(@PathParam("rule") RuleType rule);

  /**
   * Updates the configuration for a globally configured rule.
   *
   * This operation can fail for the following reasons:
   *
   * * Invalid rule name/type (HTTP error `400`)
   * * No rule with name/type `rule` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{rule}")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  Rule updateGlobalRuleConfig(@PathParam("rule") RuleType rule, Rule data);

  /**
   * Deletes a single global rule.  If this is the only rule configured, this is the same
   * as deleting **all** rules. Default global rules that have been configured via
   * `registry.rules.global` environment variables cannot be deleted.
   *
   * This operation can fail for the following reasons:
   *
   * * Invalid rule name/type (HTTP error `400`)
   * * No rule with name/type `rule` exists (HTTP error `404`)
   * * Default rule with name/type `rule` cannot be deleted (HTTP error `409`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{rule}")
  @DELETE
  void deleteGlobalRule(@PathParam("rule") RuleType rule);

  /**
   * Gets a list of all the currently configured global rules (if any).
   *
   * This operation can fail for the following reasons:
   *
   * * A server error occurred (HTTP error `500`)
   *
   */
  @GET
  @Produces("application/json")
  List<RuleType> listGlobalRules();

  /**
   * Adds a rule to the list of globally configured rules.
   *
   * This operation can fail for the following reasons:
   *
   * * The rule type is unknown (HTTP error `400`)
   * * The rule already exists (HTTP error `409`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @POST
  @Consumes("application/json")
  void createGlobalRule(Rule data);

  /**
   * Deletes all globally configured rules. Default global rules that have been configured
   * via `registry.rules.global` environment variables cannot be deleted.
   *
   * This operation can fail for the following reasons:
   *
   * * A server error occurred (HTTP error `500`)
   *
   */
  @DELETE
  void deleteAllGlobalRules();
}
