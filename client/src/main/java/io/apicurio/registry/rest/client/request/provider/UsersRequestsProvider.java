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

package io.apicurio.registry.rest.client.request.provider;

import static io.apicurio.registry.rest.client.request.provider.Routes.CURRENT_USER_PATH;
import static io.apicurio.rest.client.request.Operation.GET;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.v2.beans.UserInfo;
import io.apicurio.rest.client.request.Request;


/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class UsersRequestsProvider {

//    private static final ObjectMapper mapper = new ObjectMapper();

    public static Request<UserInfo> getCurrentUserInfo() {

        return new Request.RequestBuilder<UserInfo>()
                .operation(GET)
                .path(CURRENT_USER_PATH)
                .responseType(new TypeReference<UserInfo>() {
                })
                .build();
    }

}