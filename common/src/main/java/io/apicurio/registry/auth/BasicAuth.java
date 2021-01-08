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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;

/**
 * @author carnalca@redhat.com
 */
public class BasicAuth implements Auth {

    public static final String BASIC = "Basic ";

    private final String username;
    private final String password;

    public BasicAuth(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
    /**
     * @see io.apicurio.registry.auth.Auth#apply(java.util.Map)
     */
    @Override
    public void apply(Map<String, String> requestHeaders) {
        String usernameAndPassword = username + ":" + password;
        String encoded = Base64.encodeBase64String(usernameAndPassword.getBytes(StandardCharsets.UTF_8));
        requestHeaders.put("Authorization", BASIC + encoded);
    }

    public static class Builder {
        private String username;
        private String password;

        public Builder() {
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withClientId(String password) {
            this.password = password;
            return this;
        }

        public BasicAuth build(){
            return new BasicAuth(this.username, this.password);
        }
    }

}
