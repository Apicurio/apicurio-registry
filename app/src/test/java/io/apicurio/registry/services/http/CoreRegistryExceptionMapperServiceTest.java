package io.apicurio.registry.services.http;

import io.apicurio.registry.rest.v3.beans.DifferenceLocation;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.RuleViolationProblemDetails;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.CompatibilityDifferenceDirection;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A rule violation reaches clients twice: through the deprecated top-level causes, and through the
 * ruleViolation extension added for the error contract epic. Nothing populates the newer cause
 * fields yet, so without this test a field dropped in the mapping would only surface once the
 * checkers start filling them.
 */
public class CoreRegistryExceptionMapperServiceTest {

    private CoreRegistryExceptionMapperService mapper;

    @BeforeEach
    void setUp() {
        mapper = new CoreRegistryExceptionMapperService();
        mapper.log = mock(org.slf4j.Logger.class);
        mapper.liveness = mock(io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck.class);
        mapper.livenessUtil = mock(io.apicurio.registry.metrics.health.liveness.LivenessUtil.class);
        mapper.codeMap = mock(HttpStatusCodeMap.class);
        when(mapper.codeMap.getCode(any())).thenReturn(HTTP_BAD_REQUEST);
    }

    @Test
    void ruleViolationDetailReachesBothTheTopLevelCausesAndTheExtension() {
        RuleViolationProblemDetails details = map(fullyPopulatedViolation());

        assertEquals(RuleType.COMPATIBILITY, details.getRuleViolation().getRuleType());
        assertNotNull(details.getCauses());
        assertNotNull(details.getRuleViolation().getCauses());

        assertCause(details.getCauses());
        assertCause(details.getRuleViolation().getCauses());
    }

    @Test
    void anEmptyCauseCarriesNoLocationsAndNoDirection() {
        RuleViolationProblemDetails details = map(new RuleViolation("no detail yet", "/info"));

        Consumer<List<RuleViolationCause>> check = causes -> {
            RuleViolationCause cause = causes.get(0);
            assertEquals("no detail yet", cause.getDescription());
            assertEquals("/info", cause.getContext());
            assertNull(cause.getCode());
            assertNull(cause.getDirection());
            assertNull(cause.getProposed());
            assertNull(cause.getExisting());
        };
        check.accept(details.getCauses());
        check.accept(details.getRuleViolation().getCauses());
    }

    private RuleViolationProblemDetails map(RuleViolation violation) {
        RuleViolationException rve = new RuleViolationException("Artifact failed validation",
                RuleType.COMPATIBILITY, "FULL", Set.of(violation));
        Object entity = mapper.mapException(rve).getEntity();
        return (RuleViolationProblemDetails) entity;
    }

    private static RuleViolation fullyPopulatedViolation() {
        DifferenceLocation proposed = new DifferenceLocation();
        proposed.setPointer("/properties/name/maxLength");

        DifferenceLocation existing = new DifferenceLocation();
        existing.setPointer("/properties/name/maxLength");
        existing.setReference("shared-types.json");
        existing.setVersion("3");

        RuleViolation violation = new RuleViolation("The maximum length was reduced", "/properties/name");
        violation.setCode("SUBSCHEMA_MAX_LENGTH_DECREASED");
        violation.setDirection(CompatibilityDifferenceDirection.BACKWARD);
        violation.setProposed(proposed);
        violation.setExisting(existing);
        return violation;
    }

    private static void assertCause(List<RuleViolationCause> causes) {
        assertEquals(1, causes.size());
        RuleViolationCause cause = causes.get(0);
        assertEquals("The maximum length was reduced", cause.getDescription());
        assertEquals("/properties/name", cause.getContext());
        assertEquals("SUBSCHEMA_MAX_LENGTH_DECREASED", cause.getCode());
        assertEquals(CompatibilityDifferenceDirection.BACKWARD, cause.getDirection());

        assertEquals("/properties/name/maxLength", cause.getProposed().getPointer());
        assertNull(cause.getProposed().getReference());
        assertNull(cause.getProposed().getVersion());

        assertEquals("/properties/name/maxLength", cause.getExisting().getPointer());
        assertEquals("shared-types.json", cause.getExisting().getReference());
        assertEquals("3", cause.getExisting().getVersion());
    }
}
