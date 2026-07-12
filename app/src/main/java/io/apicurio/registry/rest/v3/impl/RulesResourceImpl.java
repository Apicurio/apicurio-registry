package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Discovery endpoints for rule types and their valid configuration values.
 * <p>
 * Implemented as a standalone JAX-RS resource (same pattern as {@link DownloadsResourceImpl}) so
 * clients can call {@code GET /rules/types} without requiring a new generated resource interface.
 */
@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
@Path("/apis/registry/v3/rules")
public class RulesResourceImpl {

    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @GET
    @Path("/types")
    @Produces(MediaType.APPLICATION_JSON)
    public List<RuleType> listRuleTypes() {
        return Arrays.asList(RuleType.values());
    }

    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    @GET
    @Path("/types/{ruleType}/values")
    @Produces(MediaType.APPLICATION_JSON)
    public RuleTypeValues listRuleTypeValues(@PathParam("ruleType") String ruleType) {
        RuleType type = parseRuleType(ruleType);
        RuleTypeValues response = new RuleTypeValues();
        response.setRuleType(type);
        response.setValues(configValuesFor(type));
        return response;
    }

    private static RuleType parseRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            throw new BadRequestException("Rule type must not be empty.");
        }
        try {
            return RuleType.fromValue(ruleType);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown rule type: " + ruleType
                    + ". Valid values are: "
                    + Stream.of(RuleType.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    private static List<String> configValuesFor(RuleType ruleType) {
        return switch (ruleType) {
            case VALIDITY -> Stream.of(ValidityLevel.values()).map(Enum::name).toList();
            case COMPATIBILITY -> Stream.of(CompatibilityLevel.values()).map(Enum::name).toList();
            case INTEGRITY -> Stream.of(IntegrityLevel.values()).map(Enum::name).toList();
        };
    }

    /**
     * Response body for {@code GET /rules/types/{ruleType}/values}.
     * Kept next to the resource to avoid clashing with a future codegen bean of the same OpenAPI name.
     */
    public static class RuleTypeValues {
        private RuleType ruleType;
        private List<String> values;

        public RuleType getRuleType() {
            return ruleType;
        }

        public void setRuleType(RuleType ruleType) {
            this.ruleType = ruleType;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}
