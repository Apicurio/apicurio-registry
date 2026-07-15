package io.apicurio.registry.mcp;

import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationCause;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsErrorFormattingTest {

    @Test
    void formatProblemDetailsOmitsRoleNamesByDefault() {
        var error = new ProblemDetails();
        error.setStatus(403);
        error.setTitle("User joel is not authorized to perform the requested operation.");
        error.setDetail("ForbiddenException: User joel is not authorized to perform the requested operation.");
        error.setName("ForbiddenException");

        String message = Utils.formatRegistryApiError(error);

        assertTrue(message.contains("HTTP 403 Forbidden"));
        assertTrue(message.contains("User joel is not authorized"));
        assertFalse(message.contains("sr-readonly"));
        assertFalse(message.contains("sr-developer"));
        assertFalse(message.contains("sr-admin"));
    }

    @Test
    void formatProblemDetailsIncludesRoleNamesWhenHintsEnabled() {
        var error = new ProblemDetails();
        error.setStatus(403);
        error.setTitle("User joel is not authorized to perform the requested operation.");
        error.setDetail("ForbiddenException: User joel is not authorized to perform the requested operation.");
        error.setName("ForbiddenException");

        String message = Utils.formatRegistryApiError(error, true);

        assertTrue(message.contains("HTTP 403 Forbidden"));
        assertTrue(message.contains("User joel is not authorized"));
        assertTrue(message.contains("sr-readonly"));
    }

    @Test
    void formatProblemDetailsNeverIncludesRoleNamesFor401() {
        var error = new ProblemDetails();
        error.setStatus(401);
        error.setTitle("Unauthorized");
        error.setDetail("Missing or invalid credentials");

        String message = Utils.formatRegistryApiError(error, true);

        assertTrue(message.contains("HTTP 401 Unauthorized"));
        assertFalse(message.contains("sr-readonly"));
    }

    @Test
    void formatProblemDetailsFallsBackToTitleWhenDetailMissing() {
        var error = new ProblemDetails();
        error.setStatus(403);
        error.setTitle("User joel is not authorized to perform the requested operation.");

        String message = Utils.formatRegistryApiError(error);

        assertTrue(message.contains("User joel is not authorized"));
    }

    @Test
    void formatRuleViolationProblemDetailsIncludesCauses() {
        var cause = new RuleViolationCause();
        cause.setContext("SYNTAX");
        cause.setDescription("Invalid JSON Schema");

        var error = new RuleViolationProblemDetails();
        error.setStatus(409);
        error.setTitle("Rule violation");
        error.setCauses(List.of(cause));

        String message = Utils.formatRegistryApiError(error);

        assertTrue(message.contains("HTTP 409"));
        assertTrue(message.contains("SYNTAX: Invalid JSON Schema"));
    }
}
