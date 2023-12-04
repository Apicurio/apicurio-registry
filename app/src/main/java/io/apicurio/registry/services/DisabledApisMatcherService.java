package io.apicurio.registry.services;

import io.apicurio.common.apps.config.Info;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


@ApplicationScoped
public class DisabledApisMatcherService {

    @Inject
    Logger log;

    private static final String UI_PATTERN = "/ui/.*";

    private final List<Pattern> disabledPatternsList = new ArrayList<>();

    @Inject
    @ConfigProperty(name = "registry.disable.apis")
    @Info(category = "api", description = "Disable APIs", availableSince = "2.0.0.Final")
    Optional<List<String>> disableRegexps;

    public void init(@Observes StartupEvent ev) {

        List<String> regexps = new ArrayList<>();

        disableRegexps.ifPresent(regexps::addAll);
        for (String regexp : regexps) {
            try {
                Pattern p = Pattern.compile(regexp);
                disabledPatternsList.add(p);
            } catch (PatternSyntaxException e) {
                log.error("An error occurred parsing a regexp for disabling APIs: " + regexp, e);
            }
        }
    }

    public boolean isDisabled(String requestPath) {
        for (Pattern pattern : disabledPatternsList) {
            if (pattern.matcher(requestPath).matches()) {
                log.warn("Request {} is rejected because it's disabled by pattern {}", requestPath, pattern.pattern());
                return true;
            }
        }
        return false;
    }

}
