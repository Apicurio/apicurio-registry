package io.apicurio.registry.auth.opawasm;

import io.apicurio.registry.utils.tests.AuthTestProfile;

import java.net.URL;
import java.util.Map;

public class OpaWasmAuthTestProfile extends AuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new java.util.HashMap<>(super.getConfigOverrides());

        URL wasmUrl = getClass().getClassLoader().getResource("registry-authz.wasm");
        URL grantsUrl = getClass().getClassLoader().getResource("opa-integration-test-grants.json");

        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.auth.opa-wasm.enabled", "true");
        props.put("apicurio.auth.opa-wasm.policy.path", wasmUrl != null ? wasmUrl.getPath() : "");
        props.put("apicurio.auth.opa-wasm.data.path", grantsUrl != null ? grantsUrl.getPath() : "");
        props.put("apicurio.auth.opa-wasm.pool-size", "2");

        return props;
    }
}
