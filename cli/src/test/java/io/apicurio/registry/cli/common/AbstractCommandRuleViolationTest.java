package io.apicurio.registry.cli.common;

import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationCause;
import io.apicurio.registry.rest.client.models.RuleViolationDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCommandRuleViolationTest {

    @Test
    public void testHasRuleViolationCauses() {
        ProblemDetails pdWithoutRuleViolation = new ProblemDetails();
        assertThat(AbstractCommand.hasRuleViolationCauses(pdWithoutRuleViolation)).isFalse();

        ProblemDetails pdWithNullCauses = new ProblemDetails();
        pdWithNullCauses.setRuleViolation(new RuleViolationDetails());
        assertThat(AbstractCommand.hasRuleViolationCauses(pdWithNullCauses)).isFalse();

        ProblemDetails pdWithEmptyCauses = new ProblemDetails();
        RuleViolationDetails emptyDetails = new RuleViolationDetails();
        emptyDetails.setCauses(Collections.emptyList());
        pdWithEmptyCauses.setRuleViolation(emptyDetails);
        assertThat(AbstractCommand.hasRuleViolationCauses(pdWithEmptyCauses)).isFalse();

        ProblemDetails pdWithCauses = new ProblemDetails();
        RuleViolationDetails details = new RuleViolationDetails();
        RuleViolationCause cause = new RuleViolationCause();
        cause.setContext("/info/version");
        cause.setDescription("Version is required");
        details.setCauses(List.of(cause));
        pdWithCauses.setRuleViolation(details);
        assertThat(AbstractCommand.hasRuleViolationCauses(pdWithCauses)).isTrue();
    }

    @Test
    public void testHandleRuleViolationWithProblemDetails() {
        StringBuilder err = new StringBuilder();
        OutputBuffer buffer = new OutputBuffer(s -> {}, err::append);

        ProblemDetails pd = new ProblemDetails();
        pd.setDetail("Validation failed");

        RuleViolationDetails details = new RuleViolationDetails();
        RuleViolationCause cause1 = new RuleViolationCause();
        cause1.setContext("/paths");
        cause1.setDescription("Must have at least one path");

        RuleViolationCause cause2 = new RuleViolationCause();
        cause2.setContext("/info/title");
        cause2.setDescription("Title cannot be empty");

        details.setCauses(List.of(cause1, cause2));
        pd.setRuleViolation(details);

        AbstractCommand.handleRuleViolation(buffer, pd);
        buffer.print();

        String output = err.toString();
        assertThat(output).contains("Error: Validation failed");
        assertThat(output).contains("  -> /paths: Must have at least one path");
        assertThat(output).contains("  -> /info/title: Title cannot be empty");
    }

    @Test
    public void testHandleRuleViolationParityWithRuleViolationProblemDetails() {
        StringBuilder err1 = new StringBuilder();
        OutputBuffer buffer1 = new OutputBuffer(s -> {}, err1::append);

        StringBuilder err2 = new StringBuilder();
        OutputBuffer buffer2 = new OutputBuffer(s -> {}, err2::append);

        RuleViolationCause cause = new RuleViolationCause();
        cause.setContext("/paths");
        cause.setDescription("Path missing");

        RuleViolationProblemDetails rvpd = new RuleViolationProblemDetails();
        rvpd.setDetail("Rule violated");
        rvpd.setCauses(List.of(cause));

        ProblemDetails pd = new ProblemDetails();
        pd.setDetail("Rule violated");
        RuleViolationDetails rvd = new RuleViolationDetails();
        rvd.setCauses(List.of(cause));
        pd.setRuleViolation(rvd);

        AbstractCommand.handleRuleViolation(buffer1, rvpd);
        buffer1.print();

        AbstractCommand.handleRuleViolation(buffer2, pd);
        buffer2.print();

        assertThat(err2.toString()).isEqualTo(err1.toString());
    }
}
