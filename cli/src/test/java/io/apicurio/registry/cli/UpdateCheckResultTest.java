package io.apicurio.registry.cli;

import io.apicurio.registry.cli.services.CliVersion;
import io.apicurio.registry.cli.services.UpdateCheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UpdateCheckResultTest {

    @Test
    public void testNoUpdates() {
        var result = new UpdateCheckResult(CliVersion.parse("3.2.4"), List.of());
        assertThat(result.hasUpdates()).isFalse();
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.unambiguousUpdate()).isNull();
    }

    @Test
    public void testSinglePatchCandidate() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.2.5"))
        );
        assertThat(result.hasUpdates()).isTrue();
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.unambiguousUpdate().toString()).isEqualTo("3.2.5");
    }

    @Test
    public void testSingleMinorCandidate() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.3.0"))
        );
        assertThat(result.hasUpdates()).isTrue();
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.unambiguousUpdate().toString()).isEqualTo("3.3.0");
    }

    @Test
    public void testPatchAndMinorAutoSelectsPatch() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.2.5"), CliVersion.parse("3.3.0"))
        );
        assertThat(result.hasUpdates()).isTrue();
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.unambiguousUpdate().toString()).isEqualTo("3.2.5");
        assertThat(result.patchUpdate().toString()).isEqualTo("3.2.5");
    }

    @Test
    public void testMultipleMinorsWithoutPatchIsAmbiguous() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.3.0"), CliVersion.parse("4.0.0"))
        );
        assertThat(result.hasUpdates()).isTrue();
        assertThat(result.isAmbiguous()).isTrue();
        assertThat(result.unambiguousUpdate()).isNull();
    }

    @Test
    public void testThreeCandidatesWithPatchAutoSelectsPatch() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.2.5"), CliVersion.parse("3.3.0"), CliVersion.parse("4.0.0"))
        );
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.unambiguousUpdate().toString()).isEqualTo("3.2.5");
    }

    @Test
    public void testFormatMessageSingle() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.2.5"))
        );
        var sb = new StringBuilder();
        result.formatMessage(sb);
        assertThat(sb.toString())
                .contains("3.2.5")
                .contains("patch")
                .contains("acr update");
    }

    @Test
    public void testFormatMessageMultipleWithPatchAndMinor() {
        var result = new UpdateCheckResult(
                CliVersion.parse("3.2.4"),
                List.of(CliVersion.parse("3.2.5"), CliVersion.parse("3.3.0"))
        );
        var sb = new StringBuilder();
        result.formatMessage(sb);
        assertThat(sb.toString())
                .contains("3.2.5")
                .contains("patch")
                .contains("3.3.0")
                .contains("minor")
                .contains("acr update 3.2.5")
                .contains("acr update 3.3.0");
    }
}
