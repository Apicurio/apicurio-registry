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

package io.apicurio.registry.client.request;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.NotAuthorizedException;


import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.rest.beans.Error;
import okhttp3.Headers;
import okhttp3.Headers.Builder;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final Auth auth;

    public AuthInterceptor(Auth auth) {
        this.auth = auth;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final Request request = chain.request();
        Map<String, String> headers = new HashMap<>();
        try {
            auth.apply(headers);
        } catch (Exception e) {
            if (e instanceof NotAuthorizedException) {
                NotAuthorizedException nae = (NotAuthorizedException) e;
                Error error = new Error();
                error.setErrorCode(nae.getResponse().getStatus());
                error.setMessage(nae.getResponse().getStatusInfo().getReasonPhrase());
                error.setDetail(Optional.ofNullable(nae.getChallenges()).map(Object::toString).orElse(null));
                throw new io.apicurio.registry.client.exception.NotAuthorizedException(error);
            }
            throw e;
        }

        Builder builder = request.headers().newBuilder();
        headers.entrySet().forEach(entry -> builder.add(entry.getKey(), entry.getValue()));
        final Headers requestHeaders = builder.build();

        final Request requestWithHeathers = request.newBuilder()
                .headers(requestHeaders)
                .build();

        return chain.proceed(requestWithHeathers);
    }
}
