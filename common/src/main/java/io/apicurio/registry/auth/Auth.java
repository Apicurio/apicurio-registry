/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.auth;

import java.util.Map;

/**
 * @author carnalca@redhat.com
 */
public interface Auth {
    
    /**
     * Called to apply this auth mechanism to the HTTP request headers.  Typically an implementation
     * would add an "Authorization" header or something similar.
     * @param requestHeaders
     */
    public void apply(Map<String, String> requestHeaders);

//    private final CredentialsConfig config;
//
//    public Auth(CredentialsConfig config) {
//        this.config = config;
//    }
//
//    public AuthStrategy getAuthStrategy() {
//
//        if (config instanceof ClientCredentialsConfig) {
//            return new KeycloakAuth((ClientCredentialsConfig) config);
//        } else if (config instanceof BasicCredentialsConfig) {
//            return new BasicAuth((BasicCredentialsConfig) config);
//        }
//        throw new IllegalStateException("Invalid credentials configuration class");
//    }
}
