package io.apicurio.registry.cli;

import io.apicurio.registry.cli.utils.UserEnvironment;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the user environment variables the installer sets, instead of persisting them.
 * Replaces the real implementation so tests never modify the environment of the user running
 * the build, and so the installer's Path handling can be asserted on.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestUserEnvironment extends UserEnvironment {

    private final Map<String, String> variables = new LinkedHashMap<>();
    private int setCallCount;

    @Override
    public String getUserVariable(final String name) {
        return variables.get(name);
    }

    @Override
    public void setUserVariable(final String name, final String value) {
        variables.put(name, value);
        setCallCount++;
    }

    /**
     * Seeds a variable as if it had already been persisted by an earlier install.
     */
    public void seed(final String name, final String value) {
        variables.put(name, value);
    }

    public int getSetCallCount() {
        return setCallCount;
    }

    public void reset() {
        variables.clear();
        setCallCount = 0;
    }
}
