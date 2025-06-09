package io.apicurio.registry.script;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScriptingService {

    @Inject
    ArtifactTypeScriptProviderContext context;

    public ArtifactTypeScriptProvider createScriptProvider(String scriptLocation) {
        String script = ScriptInterfaceUtils.loadScriptLibrary(scriptLocation);
        ArtifactTypeScriptProvider_Proxy proxy = new ArtifactTypeScriptProvider_Proxy(script, context);
        return proxy;
    }

}
