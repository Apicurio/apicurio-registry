package io.registry.service;

import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.types.RuleType;
import retrofit2.http.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;


public interface RulesService {

    @GET("rules/{rule}")
    Rule getGlobalRuleConfig(@Path("rule") RuleType rule);

    @PUT("rules/{rule}")
    Rule updateGlobalRuleConfig(@Path("rule") RuleType rule, Rule data);

    @DELETE("rules/{rule}")
    void deleteGlobalRule(@Path("rule") RuleType rule);

    @GET("rules")
    List<RuleType> listGlobalRules();

    @POST("rules")
    void createGlobalRule(Rule data);

    @DELETE("rules")
    void deleteAllGlobalRules();
}
