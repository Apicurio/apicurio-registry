package io.apicurio.registry.rest.v3.beans;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.types.CompatibilityDifferenceDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RuleViolationCauseTest {

    @Test
    void testSerialization_roundTripsNewFieldsIncludingNestedLocations() throws Exception {
        DifferenceLocation proposed = new DifferenceLocation();
        proposed.setPointer("/properties/name/maxLength");

        DifferenceLocation existing = new DifferenceLocation();
        existing.setPointer("/properties/name/maxLength");
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
        assertNull(roundTripped.getProposed().getVersion());

        assertNotNull(roundTripped.getExisting());
        assertEquals("/properties/name/maxLength", roundTripped.getExisting().getPointer());
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
}
