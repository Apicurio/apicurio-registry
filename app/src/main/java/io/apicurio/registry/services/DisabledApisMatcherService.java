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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.mt.MultitenancyProperties;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class DisabledApisMatcherService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final String UI_PATTERN = "/ui/.*";

    private List<Pattern> disabledPatternsList;

    @Inject
    MultitenancyProperties mtProperties;

    @Inject
    @ConfigProperty(name = "registry.disable.apis")
    Optional<List<String>> disableRegexps;

    @PostConstruct
    public void init() {
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
