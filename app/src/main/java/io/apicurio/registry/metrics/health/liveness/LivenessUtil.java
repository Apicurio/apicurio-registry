package io.apicurio.registry.metrics.health.liveness;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.services.http.RegistryExceptionMapperService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class LivenessUtil {

    @Inject
    Logger log;

    @Inject
    @ConfigProperty(name = "apicurio.liveness.errors.ignored")
    @Info(category = "health", description = "Ignored liveness errors", availableSince = "1.2.3.Final")
    Optional<List<String>> ignored;

    public boolean isIgnoreError(Throwable ex) {
        boolean ignored = this.isIgnored(ex);
        if (ignored) {
            log.debug("Ignored intercepted exception: " + ex.getClass().getName() + " :: " + ex.getMessage());
        }
        return ignored;
    }

    private boolean isIgnored(Throwable ex) {
        Set<Class<? extends Exception>> ignoredClasses = RegistryExceptionMapperService.getIgnored();
        if (ignoredClasses.contains(ex.getClass())) {
            return true;
        }
        return this.ignored.isPresent() && this.ignored.get().contains(ex.getClass().getName());
    }

}
