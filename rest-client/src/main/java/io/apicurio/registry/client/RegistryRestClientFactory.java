/*
 * Copyright 2020 JBoss Inc
 * Copyright 2020 IBM
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

package io.apicurio.registry.client;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryRestClientFactory {

    public static RegistryRestClient create(String baseUrl) {
        // Check if url includes user/password
        // and add auth header if it does
        HttpUrl url = HttpUrl.parse(baseUrl);
        String user = url.encodedUsername();
        String pwd = url.encodedPassword();
        if (user != null) {
            String credentials = Credentials.basic(user, pwd);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", credentials);
            return new RegistryRestClientImpl(baseUrl, headers);
        } else {
            return new RegistryRestClientImpl(baseUrl);
        }

    }

    public static RegistryRestClient create(String baseUrl, OkHttpClient okHttpClient) {
        return new RegistryRestClientImpl(baseUrl, okHttpClient);
    }

    public static RegistryRestClient create(String baseUrl, Map<String, String> headers) {
        return new RegistryRestClientImpl(baseUrl, headers);
    }

}
