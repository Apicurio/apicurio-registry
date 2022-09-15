/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.mt.MultitenancyProperties;
import io.quarkus.runtime.StartupEvent;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class DisabledApisMatcherService {

    @Inject
    Logger log;

    private static final String UI_PATTERN = "/ui/.*";
    private static final String APIS_PATTERN = "/apis/.*";
    private static final String API_PATTERN = "/api/.*";

    private List<Pattern> disabledPatternsList;

    private List<Pattern> apisPatterns;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    @ConfigProperty(name = "registry.disable.apis")
    @Info(category = "api", description = "Disable APIs", availableSince = "2.0.0.Final")
    Optional<List<String>> disableRegexps;

    public void init(@Observes StartupEvent ev) {
        disabledPatternsList = new ArrayList<>();
        List<String> regexps = new ArrayList<>();
        if (mtProperties.isMultitenancyEnabled()) {
            log.debug("Adding UI to disabled APIs, direct access to UI is disabled in multitenancy deployments");
            regexps.add(UI_PATTERN);
        }
        if (disableRegexps.isPresent()) {
            regexps.addAll(disableRegexps.get());
        }
        for (String regexp : regexps) {
            try {
                Pattern p = Pattern.compile(regexp);
                disabledPatternsList.add(p);
            } catch (PatternSyntaxException e) {
                log.error("An error occurred parsing a regexp for disabling APIs: " + regexp, e);
            }
        }

        apisPatterns = Stream.of(APIS_PATTERN, API_PATTERN)
                .map(r -> Pattern.compile(r))
                .collect(Collectors.toList());
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

    public boolean isApiRequest(String requestPath) {
        for (Pattern pattern : apisPatterns) {
            if (pattern.matcher(requestPath).matches()) {
                return true;
            }
        }
        return false;
    }

}
