package io.apicurio.registry.script;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ScriptingService {

    @Inject
    ArtifactTypeScriptProviderContext context;

    Map<String, ArtifactTypeScriptProvider> cache = new HashMap();

    public ArtifactTypeScriptProvider createScriptProvider(String scriptLocation) {
        if (cache.containsKey(scriptLocation)) {
            return cache.get(scriptLocation);
        } else {
            String script = ScriptInterfaceUtils.loadScriptLibrary(scriptLocation);
            ArtifactTypeScriptProvider_Proxy proxy = new ArtifactTypeScriptProvider_Proxy(script, context);
            cache.put(scriptLocation, proxy);
            return proxy;
        }
    }

}
