package io.apicurio.registry.script;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScriptingService {

    public <I, O> O executeScript(String script, I input, Class<O> outputClass) throws ScriptExecutionException {
        try {
            // Execute script here using chicory
            throw new UnsupportedOperationException("Scripting support not implemented yet");
        } catch (Exception e) {
            throw new ScriptExecutionException(e);
        }
    }

}
