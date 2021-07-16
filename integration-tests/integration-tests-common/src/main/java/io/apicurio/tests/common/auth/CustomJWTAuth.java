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

package io.apicurio.tests.common.auth;

import java.util.Map;

import io.apicurio.registry.auth.Auth;
import io.smallrye.jwt.build.Jwt;

/**
 * @author Fabian Martinez
 */
public class CustomJWTAuth implements Auth {

    private String username;
    private String organizationId;

    public CustomJWTAuth(String username, String organizationId) {
        this.username = username;
        this.organizationId = organizationId;
    }

    /**
     * @see io.apicurio.registry.auth.Auth#apply(java.util.Map)
     */
    @Override
    public void apply(Map<String, String> requestHeaders) {
        requestHeaders.put("Authorization", "Bearer " +
                Jwt.preferredUserName(username)

                    //TODO remove this and update multitenancy ITs to store roles in the DB
                    .groups("sr-admin")

                    .claim("rh_org_id", organizationId)
                    .jws()
                    .keyId("1")
                    .sign());
//                    .sign("privateKey.jwk"));
    }

}
