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

import io.apicurio.rest.client.auth.Auth;
import io.smallrye.jwt.build.Jwt;

/**
 * @author Fabian Martinez
 */
public class CustomJWTAuth implements Auth {

    public static final String RH_ORG_ID_CLAIM = "rh-org-id";
    public static final String ORG_ID = "org_id";

    private String username;
    private String organizationId;

    public CustomJWTAuth(String username, String organizationId) {
        this.username = username;
        this.organizationId = organizationId;
    }

    @Override
    public void apply(Map<String, String> requestHeaders) {
        String token = Jwt.preferredUserName(username)
                .claim(RH_ORG_ID_CLAIM, organizationId)
                .claim(ORG_ID, organizationId)
                .jws()
                .keyId("1")
                .sign();

        requestHeaders.put("Authorization", "Bearer " + token);
    }

}
