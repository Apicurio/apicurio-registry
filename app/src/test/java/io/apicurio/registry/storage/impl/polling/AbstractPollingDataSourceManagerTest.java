package io.apicurio.registry.storage.impl.polling;

import io.apicurio.registry.storage.impl.polling.format.FormatVersion;
import io.apicurio.registry.storage.impl.polling.model.Any;
import io.apicurio.registry.storage.impl.polling.model.Type;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AbstractPollingDataSourceManager#warnAboutDeprecatedFormatVersions}: one
 * warning per deprecated version per load, naming the file count and an example path, never one
 * warning per file (every change re-reads every file).
 */
class AbstractPollingDataSourceManagerTest {

    private static class TestManager extends AbstractPollingDataSourceManager<SourceMarker> {
        @Override
        public void start() {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public PollingResult<SourceMarker> poll() {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static PollingDataFile fileWithVersion(String path, FormatVersion version) {
        PollingDataFile file = mock(PollingDataFile.class, CALLS_REAL_METHODS);
        var fileAny = Any.builder().type(Type.ARTIFACT).formatVersion(version).build();
        when(file.getAny()).thenReturn(Optional.of(fileAny));
        when(file.getPath()).thenReturn(path);
        when(file.getSourceId()).thenReturn("test");
        return file;
    }

    /**
     * Guards against a regression to one warning per file: four deprecated files must still produce
     * exactly one log call (the default for {@code verify(...)}), not four, with the file count and
     * an example path.
     */
    @Test
    void oneWarningPerLoadForFourV0Files() {
        TestManager manager = new TestManager();
        manager.log = mock(Logger.class);

        ProcessingState state = new ProcessingState(null, null);
        state.index(fileWithVersion("b.registry.yaml", FormatVersion.V0));
        state.index(fileWithVersion("a.registry.yaml", FormatVersion.V0));
        state.index(fileWithVersion("d.registry.yaml", FormatVersion.V0));
        state.index(fileWithVersion("c.registry.yaml", FormatVersion.V0));

        manager.warnAboutDeprecatedFormatVersions(state);

        verify(manager.log).warn(eq("{} metadata file(s) (for example {}) use the GitOps data format version"
                + " {}, which is deprecated since {} and will be removed in a future release. Convert them to"
                + " version {}: change $type from '<kind>-{}' to '<kind>-{}', for example '{}'."),
                eq(4), eq("a.registry.yaml"), eq("v0"), anyString(), eq("v1"), eq("v0"), eq("v1"),
                eq("artifact-v1"));
    }

    @Test
    void oneWarningForOneV0FileInAMixedRepository() {
        TestManager manager = new TestManager();
        manager.log = mock(Logger.class);

        ProcessingState state = new ProcessingState(null, null);
        state.index(fileWithVersion("old.registry.yaml", FormatVersion.V0));
        state.index(fileWithVersion("new.registry.yaml", FormatVersion.V1));

        manager.warnAboutDeprecatedFormatVersions(state);

        verify(manager.log).warn(anyString(), eq(1), eq("old.registry.yaml"), eq("v0"), anyString(), eq("v1"),
                eq("v0"), eq("v1"), eq("artifact-v1"));
    }

    @Test
    void noWarningForAV1OnlyRepository() {
        TestManager manager = new TestManager();
        manager.log = mock(Logger.class);

        ProcessingState state = new ProcessingState(null, null);
        state.index(fileWithVersion("a.registry.yaml", FormatVersion.V1));

        manager.warnAboutDeprecatedFormatVersions(state);

        // verify(..., never()).warn(anyString(), (Object[]) any()) doesn't match the real varargs
        // call and would pass even if a warning were logged; verifyNoInteractions does fail on it.
        verifyNoInteractions(manager.log);
    }
}
