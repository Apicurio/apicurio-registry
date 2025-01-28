package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;

import java.util.Set;
import java.util.TreeSet;

/**
 * Models the value of the JAVA_OPTS_APPEND environment variable. This is a convenient way to merge values and
 * handle conflicts.
 */
public class JavaOptsAppend {

    private final Set<String> opts = new TreeSet<>();

    /**
     * Called to set the initial value of the ENV var using an existing value, typically configured in the
     * "env" section of the CR. This will be a value set by the app deployer, with runtime options they want
     * to have enabled.
     */
    public void setOptsFromEnvVar(String value) {
        opts.clear();
        if (value != null && !value.trim().isEmpty()) {
            opts.addAll(Set.of(value.split(" ")));
        }
    }

    /**
     * Converts this object into an {@link EnvVar} instance.
     */
    public EnvVar toEnvVar() {
        EnvVarBuilder builder = new EnvVarBuilder();
        return builder.withName(EnvironmentVariables.JAVA_OPTS_APPEND).withValue(String.join(" ", opts))
                .build();
    }

    /**
     * Add another option. If the option already exists, do nothing. Whether the option already exists may
     * depend on the option. Custom logic may be needed to properly support certain options.
     */
    public void addOpt(String optValue) {
        if (!containsOpt(optValue)) {
            opts.add(optValue);
        }
    }

    /**
     * Returns 'true' if there are no options set.
     */
    public boolean isEmpty() {
        return opts.isEmpty();
    }

    /**
     * Returns 'true' if the option already exists. The logic for whether an option already exists depends on
     * the option.
     */
    public boolean containsOpt(String optValue) {
        if (optValue == null || optValue.trim().isEmpty()) {
            return false;
        }
        if (optValue.startsWith("-D") || optValue.startsWith("-XX:")) {
            return containsValuedParameter(optValue);
        }
        if (optValue.startsWith("-Xms")) {
            return containsXms();
        }
        if (optValue.startsWith("-Xmx")) {
            return containsXmx();
        }
        if (optValue.startsWith("-javaagent:")) {
            return containsJavaAgent();
        }
        if (optValue.startsWith("-agentlib:")) {
            return containsAgentLib();
        }
        return opts.contains(optValue);
    }

    /**
     * Returns 'true' if an option with the given prefix already exists.
     */
    public boolean containsOptByPrefix(String prefix) {
        return this.opts.stream().anyMatch(s -> s.startsWith(prefix));
    }

    /**
     * Checks if an option of one of the following form exists already:
     * <ul>
     * <li>-Dmy.property.name=foo</li>
     * <li>-XX:OptionName=bar</li>
     * </ul>
     * Looks for another option with the same name but potentially different value.
     */
    private boolean containsValuedParameter(String optValue) {
        if (optValue.contains("=")) {
            String prefix = optValue.substring(0, optValue.indexOf("="));
            return containsOptByPrefix(prefix);
        }
        return opts.contains(optValue);
    }

    /**
     * Checks for an option of the form "-Xms512m".
     */
    private boolean containsXms() {
        return containsOptByPrefix("-Xms");
    }

    /**
     * Checks for an option of the form "-Xmx1024m".
     */
    private boolean containsXmx() {
        return containsOptByPrefix("-Xmx");
    }

    /**
     * Checks for an option of the form "-javaagent:/path/to/agent.jar"
     */
    private boolean containsJavaAgent() {
        return containsOptByPrefix("-javaagent:");
    }

    /**
     * Checks for an option of the form "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
     */
    private boolean containsAgentLib() {
        return containsOptByPrefix("-agentlib:");
    }

}
