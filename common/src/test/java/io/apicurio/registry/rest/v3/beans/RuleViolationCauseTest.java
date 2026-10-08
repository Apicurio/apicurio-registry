package io.apicurio.registry.rest.v3.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.types.CompatibilityDifferenceDirection;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RuleViolationCauseTest {

    @Test
    void testSerialization_roundTripsNewFieldsIncludingNestedLocations() throws Exception {
        DifferenceLocation proposed = new DifferenceLocation();
        proposed.setPointer("/properties/name/maxLength");

        // The existing side carries both a reference (the pointer is into that referenced artifact)
        // and the version the difference was found against.
        DifferenceLocation existing = new DifferenceLocation();
        existing.setPointer("/properties/name/maxLength");
        existing.setReference("shared-types.json");
        existing.setVersion("3");

        RuleViolationCause data = new RuleViolationCause();
        data.setDescription("The 'maxLength' string-length limit was decreased.");
        data.setContext("/properties/name/maxLength");
        data.setCode("STRING_TYPE_MAX_LENGTH_DECREASED");
        data.setDirection(CompatibilityDifferenceDirection.BACKWARD);
        data.setProposed(proposed);
        data.setExisting(existing);

        ObjectMapper mapper = new ObjectMapper();
        RuleViolationCause roundTripped = mapper.readValue(mapper.writeValueAsString(data),
                RuleViolationCause.class);

        assertEquals("The 'maxLength' string-length limit was decreased.", roundTripped.getDescription());
        assertEquals("/properties/name/maxLength", roundTripped.getContext());
        assertEquals("STRING_TYPE_MAX_LENGTH_DECREASED", roundTripped.getCode());
        assertEquals(CompatibilityDifferenceDirection.BACKWARD, roundTripped.getDirection());

        assertNotNull(roundTripped.getProposed());
        assertEquals("/properties/name/maxLength", roundTripped.getProposed().getPointer());
        assertNull(roundTripped.getProposed().getReference());
        assertNull(roundTripped.getProposed().getVersion());

        assertNotNull(roundTripped.getExisting());
        assertEquals("/properties/name/maxLength", roundTripped.getExisting().getPointer());
        assertEquals("shared-types.json", roundTripped.getExisting().getReference());
        assertEquals("3", roundTripped.getExisting().getVersion());
    }

    /**
     * The new fields are additive, so a client generated from an earlier OpenAPI document has to see
     * exactly the body it saw before when nothing populates them.
     */
    @Test
    void testSerialization_omitsNewFieldsWhenUnset() throws Exception {
        RuleViolationCause data = new RuleViolationCause();
        data.setDescription("API is missing a title");
        data.setContext("/info[title]");

        String json = new ObjectMapper().writeValueAsString(data);

        assertEquals("{\"description\":\"API is missing a title\",\"context\":\"/info[title]\"}", json);
    }

    /**
     * The acceptance criterion the other way round: a response that does carry the new fields, including
     * `ruleType`, still has to be readable by a client generated before they existed.
     * {@link LegacyProblemDetails} and {@link LegacyCause} stand in for such a client, declaring only the
     * fields the 3.3 document had. This fails if a legacy field is ever renamed, removed or restructured,
     * which is what actually breaks an older client - new fields appearing is expected, and tolerating them
     * is what the generated clients do.
     */
    @Test
    void testDeserialization_clientWithoutTheNewFieldsStillReadsACompleteResponse() throws Exception {
        DifferenceLocation existing = new DifferenceLocation();
        existing.setPointer("/properties/name/maxLength");
        existing.setVersion("3");

        RuleViolationCause cause = new RuleViolationCause();
        cause.setDescription("The 'maxLength' string-length limit was decreased.");
        cause.setContext("/properties/name/maxLength");
        cause.setCode("STRING_TYPE_MAX_LENGTH_DECREASED");
        cause.setDirection(CompatibilityDifferenceDirection.BACKWARD);
        cause.setExisting(existing);

        RuleViolationProblemDetails details = new RuleViolationProblemDetails();
        details.setTitle("Artifact failed compatibility checking");
        details.setStatus(409);
        details.setCauses(List.of(cause));
        details.setRuleType(RuleType.COMPATIBILITY);

        String json = new ObjectMapper().writeValueAsString(details);

        LegacyProblemDetails legacy = new ObjectMapper().readValue(json, LegacyProblemDetails.class);

        assertEquals("Artifact failed compatibility checking", legacy.title);
        assertEquals(409, legacy.status);
        assertEquals(1, legacy.causes.size());
        assertEquals("The 'maxLength' string-length limit was decreased.", legacy.causes.get(0).description);
        assertEquals("/properties/name/maxLength", legacy.causes.get(0).context);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LegacyProblemDetails {
        public String title;
        public int status;
        public List<LegacyCause> causes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class LegacyCause {
        public String description;
        public String context;
    }
}
