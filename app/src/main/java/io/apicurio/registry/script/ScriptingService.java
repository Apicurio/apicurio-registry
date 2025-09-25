package io.apicurio.registry.script;

import io.quarkiverse.quickjs4j.ScriptInterfaceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class ScriptingService {

    @Inject
    ScriptInterfaceFactory<ArtifactTypeScriptProvider, ArtifactTypeScriptProviderContext> scriptProviderFactory;

    @Inject
    ArtifactTypeScriptProviderContext context;

    Map<String, ArtifactTypeScriptProvider> cache = new HashMap();

    public ArtifactTypeScriptProvider createScriptProvider(String scriptLocation) {
        if (cache.containsKey(scriptLocation)) {
            return cache.get(scriptLocation);
        } else {
            String script = ScriptInterfaceUtils.loadScriptLibrary(scriptLocation);
            ArtifactTypeScriptProvider provider = scriptProviderFactory.create(script, context);
            cache.put(scriptLocation, provider);
            return provider;
        }
    }

}
