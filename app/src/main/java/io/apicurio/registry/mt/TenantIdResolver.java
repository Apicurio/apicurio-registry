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
package io.apicurio.registry.mt;

import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class centralizes the logic to resolve the tenantId from an http request.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantIdResolver {

    private static final int TENANT_ID_POSITION = 2;

    @Inject
    Logger log;

    @Inject
    MultitenancyProperties mtProperties;

    Pattern subdomainNamePattern;

    String multitenancyBasePath;


    void init(@Observes StartupEvent ev) {
        if (mtProperties.isMultitenancyEnabled()) {
            log.info("Registry running with multitenancy enabled");
        }
        multitenancyBasePath = "/" + mtProperties.getNameMultitenancyBasePath() + "/";

        if (mtProperties.isMultitenancySubdomainEnabled()) {
            this.subdomainNamePattern = Pattern.compile(mtProperties.getSubdomainMultitenancyPattern());
        }
    }

    /**
     * Resolves the tenant ID from the inbound HTTP request.  The tenantId can potentially be located in one
     * of the following locations:
     * <p>
     * 1) In the context path:  https://registry.example.org/t/{tenantId}/apis/registry/v2/search/artifacts
     * 2) In a request header:  https://registry.example.org/apis/registry/v2/search/artifacts + header X-Registry-Tenant-Id: {tenantId}
     * 3) In a subdomain:       https://{tenantId}.registry.example.org/apis/registry/v2/search/artifacts
     * <p>
     * Configuration options exist to enable/disable checking each of these locations.  Additional configuration
     * options exist to modify the specific behavior of each approach.  For example, for (2) it is possible to
     * configure the name of the request header.
     *
     * @param uri
     * @param headerProvider
     * @param serverNameProvider
     * @param afterSuccessfullUrlResolution
     */
    public Optional<String> resolveTenantId(String uri, Function<String, String> headerProvider, Supplier<String> serverNameProvider,
                                            Consumer<String> afterSuccessfullUrlResolution) {

        if (mtProperties.isMultitenancyEnabled()) {
            log.trace("Resolving tenantId...");

            // Resolve tenantId from context path (if enabled)
            if (mtProperties.isMultitenancyContextPathEnabled()) {
                log.trace("Resolving tenantId from path: {}", uri);
                if (uri.startsWith(multitenancyBasePath)) {
                    String[] tokens = uri.split("/");
                    // 0 is empty
                    // 1 is t
                    // 2 is the tenantId
                    String tenantId = tokens[TENANT_ID_POSITION];
                    if (afterSuccessfullUrlResolution != null) {
                        afterSuccessfullUrlResolution.accept(tenantId);
                    }
                    return Optional.of(tenantId);
                } else {
                    log.warn("Context-path multi-tenancy enabled.  Detected unmatched path.");
                }
            }

            // Resolve tenantId from domain name (if enabled)
            if (mtProperties.isMultitenancySubdomainEnabled()) {
                log.trace("Resolving tenantId from subdomain.");

                // Get the domain name from the request (configurable to either get it from a request header or the request's server name).
                String domain = null;
                String domainLocation = mtProperties.getSubdomainMultitenancyLocation();
                if (domainLocation.equals("header")) {
                    String domainHeaderName = mtProperties.getSubdomainMultitenancyHeaderName();
                    domain = headerProvider.apply(domainHeaderName);
                } else if (domainLocation.equals("serverName")) {
                    domain = serverNameProvider.get();
                } else {
                    log.warn("Unknown domain location: " + domainLocation);
                    domain = "";
                }

                // Now try to match the domain name against the subdomain pattern to extract the tenantId.
                // E.g. 12345.example.org where "12345" is the tenantId
                Matcher matcher = this.subdomainNamePattern.matcher(domain);
                if (matcher.matches()) {
                    String tenantId = matcher.group(1);
                    if (tenantId != null) {
                        return Optional.of(tenantId);
                    }
                } else {
                    log.warn("Subdomain multi-tenancy enabled.  Detected unmatched domain: " + domain);
                }
            }

            // Resolve tenantId from request header (if enabled)
            if (mtProperties.isMultitenancyRequestHeaderEnabled()) {
                String headerName = mtProperties.getTenantIdRequestHeader();
                log.trace("Resolving tenantId from request header named: {}", headerName);
                String tenantId = headerProvider.apply(headerName);
                if (tenantId != null) {
                    return Optional.of(tenantId);
                } else {
                    log.warn("Request header multi-tenancy enabled, but header value not found in request.");
                }
            }
        }
        return Optional.empty();
    }

    public int tenantPrefixLength(String tenantId) {
        return (multitenancyBasePath + tenantId).length();
    }
}
