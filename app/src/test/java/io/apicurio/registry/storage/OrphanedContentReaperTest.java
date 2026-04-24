package io.apicurio.registry.storage;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestProfile(OrphanedContentReaperTest.DisabledProfile.class)
public class OrphanedContentReaperTest {

    @Inject
    OrphanedContentReaper reaper;

    @Inject
    Logger log;

    @Test
    public void testOrphanCleanupDisabled() {
        OrphanedContentReaper spyReaper = spy(reaper);

        RegistryStorage mockStorage = mock(RegistryStorage.class);
        when(mockStorage.isReady()).thenReturn(true);
        when(mockStorage.isReadOnly()).thenReturn(false);

        spyReaper.storage = mockStorage;
        assertDoesNotThrow(() -> spyReaper.run());

        verify(mockStorage, never()).deleteAllOrphanedContent();
        verify(mockStorage, never()).isReady();
    }

    public static class DisabledProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.of(
                "apicurio.storage.orphan-cleanup.enabled", "false"
            );
        }
    }
}
