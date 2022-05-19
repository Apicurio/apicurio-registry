/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.services.auth;

import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.AuthException;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.error.ApicurioRestClientException;
import io.apicurio.rest.client.util.IoUtil;

import java.io.InputStream;

public class CustomAuthErrorHandler extends AuthErrorHandler {

    @Override
    public ApicurioRestClientException handleErrorResponse(InputStream body, int statusCode) {
        switch(statusCode) {
            case 401:
                return new NotAuthorizedException(body != null ? IoUtil.toString(body): "");
            case 403:
                return new ForbiddenException(IoUtil.toString(body));
            default:
                return new AuthException(IoUtil.toString(body));
        }    }
}
