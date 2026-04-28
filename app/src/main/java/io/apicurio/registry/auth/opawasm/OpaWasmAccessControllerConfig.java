package io.apicurio.registry.auth.opawasm;

import java.util.Optional;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class OpaWasmAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.opa-wasm.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable OPA WASM in-process authorization", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.policy.path")
    @Info(category = CATEGORY_AUTH, description = "Path to the compiled OPA WASM policy file", availableSince = "3.0.0", experimental = true)
    Optional<String> policyPath;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.data.path")
    @Info(category = CATEGORY_AUTH, description = "Path to the JSON permissions data file for OPA policy evaluation", availableSince = "3.0.0", experimental = true)
    Optional<String> dataPath;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.pool-size", defaultValue = "4")
    @Info(category = CATEGORY_AUTH, description = "Number of OPA WASM policy instances in the evaluation pool", availableSince = "3.0.0", experimental = true)
    int poolSize;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.entrypoint", defaultValue = "registry/authz/allow")
    @Info(category = CATEGORY_AUTH, description = "OPA policy entrypoint name for authorization evaluation", availableSince = "3.0.0", experimental = true)
    String entrypoint;

    public boolean isEnabled() {
        return enabled;
    }

    public String getPolicyPath() {
        return policyPath.orElse(null);
    }

    public String getDataPath() {
        return dataPath.orElse(null);
    }

    public int getPoolSize() {
        return poolSize;
    }

    public String getEntrypoint() {
        return entrypoint;
    }
}
