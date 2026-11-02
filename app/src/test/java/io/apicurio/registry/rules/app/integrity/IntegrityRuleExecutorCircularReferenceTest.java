package io.apicurio.registry.rules.app.integrity;

import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link IntegrityRuleExecutor#execute(RuleContext)}'s circular-reference check
 * (#9095): missing references must stay a no-op, but an unexpected storage failure must abort the check
 * rather than silently report "no cycle".
 */
public class IntegrityRuleExecutorCircularReferenceTest {

    private static final String GROUP = "g1";

    private IntegrityRuleExecutor newExecutor() {
        return new IntegrityRuleExecutor();
    }

    private RuleContext contextWithReferenceTo(RegistryStorage storage, String artifactId, String version) {
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(GROUP);
        ref.setArtifactId(artifactId);
        ref.setVersion(version);
        ref.setName(artifactId + ".avsc");

        return RuleContext.builder().groupId(GROUP).artifactId("self").artifactType("AVRO")
                .configuration(IntegrityLevel.NO_CIRCULAR_REFERENCES.name()).references(List.of(ref))
                .storage(storage).build();
    }

    @Test
    public void missingReferencedArtifactIsNotTreatedAsACycle() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionContent(anyString(), anyString(), anyString()))
                .thenThrow(new ArtifactNotFoundException(GROUP, "missing"));

        IntegrityRuleExecutor executor = newExecutor();
        RuleContext context = contextWithReferenceTo(storage, "missing", "1");

        assertDoesNotThrow(() -> executor.execute(context));
    }

    @Test
    public void missingReferencedVersionIsNotTreatedAsACycle() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionContent(anyString(), anyString(), anyString()))
                .thenThrow(new VersionNotFoundException(GROUP, "a", "99"));

        IntegrityRuleExecutor executor = newExecutor();
        RuleContext context = contextWithReferenceTo(storage, "a", "99");

        assertDoesNotThrow(() -> executor.execute(context));
    }

    @Test
    public void unexpectedStorageFailureAbortsTheCheckInsteadOfReportingNoCycle() {
        RegistryStorage storage = mock(RegistryStorage.class);
        when(storage.getArtifactVersionContent(anyString(), anyString(), anyString()))
                .thenThrow(new RegistryStorageException("database unavailable"));

        IntegrityRuleExecutor executor = newExecutor();
        RuleContext context = contextWithReferenceTo(storage, "a", "1");

        // The storage failure must propagate - it must not be swallowed and reported as "no cycle".
        assertThrows(RegistryStorageException.class, () -> executor.execute(context));
    }

    @Test
    public void realCycleIsStillDetected() {
        RegistryStorage storage = mock(RegistryStorage.class);

        // a@1 -> b@1 -> a@1: a genuine cycle.
        ArtifactReferenceDto refToB = ArtifactReferenceDto.builder().groupId(GROUP).artifactId("b")
                .version("1").name("b.avsc").build();
        ArtifactReferenceDto refToA = ArtifactReferenceDto.builder().groupId(GROUP).artifactId("a")
                .version("1").name("a.avsc").build();
        StoredArtifactVersionDto aContent = StoredArtifactVersionDto.builder().references(List.of(refToB))
                .build();
        StoredArtifactVersionDto bContent = StoredArtifactVersionDto.builder().references(List.of(refToA))
                .build();

        when(storage.getArtifactVersionContent(GROUP, "a", "1")).thenReturn(aContent);
        when(storage.getArtifactVersionContent(GROUP, "b", "1")).thenReturn(bContent);

        IntegrityRuleExecutor executor = newExecutor();
        RuleContext context = contextWithReferenceTo(storage, "a", "1");

        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> executor.execute(context));
        assertEquals(IntegrityLevel.NO_CIRCULAR_REFERENCES.name(), ex.getRuleConfiguration().orElse(null));
        assertTrue(
                ex.getCauses().stream().anyMatch(cause -> cause.getDescription()
                        .contains("g1/a@1 -> g1/b@1 -> g1/a@1 (cycle)")),
                "expected the cycle path to be reported, got: " + ex.getCauses());
    }

    @Test
    public void nonCircularChainIsAllowed() {
        RegistryStorage storage = mock(RegistryStorage.class);

        // a@1 references nothing further.
        StoredArtifactVersionDto aContent = StoredArtifactVersionDto.builder().references(List.of()).build();
        when(storage.getArtifactVersionContent(GROUP, "a", "1")).thenReturn(aContent);

        IntegrityRuleExecutor executor = newExecutor();
        RuleContext context = contextWithReferenceTo(storage, "a", "1");

        assertDoesNotThrow(() -> executor.execute(context));
    }
}
