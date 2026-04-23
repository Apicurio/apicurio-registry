package io.apicurio.registry.auth.opawasm;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Singleton
public class OpaWasmAccessControllerConfig {

    @ConfigProperty(name = "apicurio.auth.opa-wasm.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable OPA WASM in-process authorization", availableSince = "3.0.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.policy.path", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Path to the compiled OPA WASM policy file", availableSince = "3.0.0", experimental = true)
    String policyPath;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.data.path", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Path to the JSON permissions data file for OPA policy evaluation", availableSince = "3.0.0", experimental = true)
    String dataPath;

    @ConfigProperty(name = "apicurio.auth.opa-wasm.pool-size", defaultValue = "4")
    @Info(category = CATEGORY_AUTH, description = "Number of OPA WASM policy instances in the evaluation pool", availableSince = "3.0.0", experimental = true)
    int poolSize;

    public boolean isEnabled() {
        return enabled;
    }

    public String getPolicyPath() {
        return policyPath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public int getPoolSize() {
        return poolSize;
    }
}
