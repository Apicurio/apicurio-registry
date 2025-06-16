package io.apicurio.registry.script;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

@ApplicationScoped
public class ArtifactTypeScriptProviderContext {

    @Inject
    Logger log;

    public void info(String message) {
        log.info(message);
    }

    public void debug(String message) {
        log.debug(message);
    }

}
