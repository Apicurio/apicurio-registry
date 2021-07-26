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

package io.apicurio.registry.services.auth;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 *  Extracts credentials pair from header encoded as base 64 if exists return null otherwise.
 */
public class CredentialsHelper {

    private static final String BASIC = "basic";
    private static final String BASIC_PREFIX = BASIC + " ";
    private static final String LOWERCASE_BASIC_PREFIX = BASIC_PREFIX.toLowerCase(Locale.ENGLISH);
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final Charset charset = StandardCharsets.UTF_8;

    public static Pair<String, String> extractCredentialsFromContext(RoutingContext context) {
        List<String> authHeaders = context.request().headers().getAll(HttpHeaderNames.AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.toLowerCase(Locale.ENGLISH).startsWith(LOWERCASE_BASIC_PREFIX)) {
                    String base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge;
                    byte[] decode = Base64.getDecoder().decode(base64Challenge);

                    plainChallenge = new String(decode, charset);
                    int colonPos;
                    String COLON = ":";
                    if ((colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        String userName = plainChallenge.substring(0, colonPos);
                        String password = plainChallenge.substring(colonPos + 1);
                        return new ImmutablePair<>(userName, password);
                    }
                }
            }
        }
        //XX Intended null return
        return null;
    }
}
