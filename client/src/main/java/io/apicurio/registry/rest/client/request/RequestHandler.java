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

package io.apicurio.registry.rest.client.request;

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.utils.BooleanUtil;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.keycloak.authorization.client.util.HttpResponseException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_HEADERS_PREFIX;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_KEYSTORE_LOCATION;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_KEYSTORE_PASSWORD;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_KEYSTORE_TYPE;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_KEY_PASSWORD;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_TRUSTSTORE_LOCATION;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_TRUSTSTORE_PASSWORD;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_REQUEST_TRUSTSTORE_TYPE;
import static io.apicurio.registry.rest.client.config.ClientConfig.REGISTRY_CLIENT_DISABLE_AUTO_BASE_PATH_APPEND;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class RequestHandler {

    private static final String BASE_PATH = "apis/registry/v2/";

    private final HttpClient client;
    private final String endpoint;
    private Auth auth;
    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();
    private static final ThreadLocal<Map<String, String>> requestHeaders = ThreadLocal.withInitial(Collections::emptyMap);

    public RequestHandler(String endpoint, Map<String, Object> configs, Auth auth) {
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        Object disableAutoBasePathAppend = configs.get(REGISTRY_CLIENT_DISABLE_AUTO_BASE_PATH_APPEND);
        if (!BooleanUtil.toBoolean(disableAutoBasePathAppend)) {
            if (!endpoint.endsWith(BASE_PATH)) {
                endpoint += BASE_PATH;
            }
        }

        final HttpClient.Builder httpClientBuilder = handleConfiguration(configs);
        this.endpoint = endpoint;
        this.auth = auth;
        this.client = httpClientBuilder.build();
    }

    private static HttpClient.Builder handleConfiguration(Map<String, Object> configs) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        clientBuilder.version(Version.HTTP_1_1);
        addHeaders(configs);
        clientBuilder = addSSL(clientBuilder, configs);
        return clientBuilder;
    }

    private static void addHeaders(Map<String, Object> configs) {

        Map<String, String> requestHeaders = configs.entrySet().stream()
                .filter(map -> map.getKey().startsWith(REGISTRY_REQUEST_HEADERS_PREFIX))
                .collect(Collectors.toMap(map -> map.getKey()
                        .replace(REGISTRY_REQUEST_HEADERS_PREFIX, ""), map -> map.getValue().toString()));

        if (!requestHeaders.isEmpty()) {
            requestHeaders.forEach(DEFAULT_HEADERS::put);
        }
    }

    private static HttpClient.Builder addSSL(HttpClient.Builder httpClientBuilder, Map<String, Object> configs) {

        try {
            KeyManager[] keyManagers = getKeyManagers(configs);
            TrustManager[] trustManagers = getTrustManagers(configs);

            if (trustManagers != null && (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager))) {
                throw new IllegalStateException("A single X509TrustManager is expected. Unexpected trust managers: " + Arrays.toString(trustManagers));
            }

            if (keyManagers != null || trustManagers != null) {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
                return httpClientBuilder.sslContext(sslContext);
            } else {
                return httpClientBuilder;
            }
        } catch (IOException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException | KeyManagementException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TrustManager[] getTrustManagers(Map<String, Object> configs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        TrustManager[] trustManagers = null;

        if (configs.containsKey(REGISTRY_REQUEST_TRUSTSTORE_LOCATION)) {
            String truststoreType = (String) configs.getOrDefault(REGISTRY_REQUEST_TRUSTSTORE_TYPE, "JKS");
            KeyStore truststore = KeyStore.getInstance(truststoreType);
            String truststorePwd = (String) configs.getOrDefault(REGISTRY_REQUEST_TRUSTSTORE_PASSWORD, "");
            truststore.load(new FileInputStream((String) configs.get(REGISTRY_REQUEST_TRUSTSTORE_LOCATION)), truststorePwd.toCharArray());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(truststore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }
        return trustManagers;
    }

    private static KeyManager[] getKeyManagers(Map<String, Object> configs) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        KeyManager[] keyManagers = null;

        if (configs.containsKey(REGISTRY_REQUEST_KEYSTORE_LOCATION)) {
            String keystoreType = (String) configs.getOrDefault(REGISTRY_REQUEST_KEYSTORE_TYPE, "JKS");
            KeyStore keystore = KeyStore.getInstance(keystoreType);
            String keyStorePwd = (String) configs.getOrDefault(REGISTRY_REQUEST_KEYSTORE_PASSWORD, "");
            keystore.load(new FileInputStream((String) configs.get(REGISTRY_REQUEST_KEYSTORE_LOCATION)), keyStorePwd.toCharArray());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            // If no key password provided, try using the keystore password
            String keyPwd = (String) configs.getOrDefault(REGISTRY_REQUEST_KEY_PASSWORD, keyStorePwd);
            keyManagerFactory.init(keystore, keyPwd.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }
        return keyManagers;
    }

    public <T> T sendRequest(Request<T> request) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(buildURI(endpoint + request.getRequestPath(), request.getQueryParams(), request.getPathParams()));

            DEFAULT_HEADERS.forEach(requestBuilder::header);

            //Add current request headers
            requestHeaders.get().forEach(requestBuilder::header);
            requestHeaders.remove();

            Map<String, String> headers = request.getHeaders();
            if (this.auth != null) {
                //make headers mutable...
                headers = new HashMap<>(headers);
                this.auth.apply(headers);
            }
            headers.forEach(requestBuilder::header);

            switch (request.getOperation()) {
                case GET:
                    requestBuilder.GET();
                    break;
                case PUT:
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getData().readAllBytes()));
                    break;
                case POST:
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(request.getData().readAllBytes()));
                    break;
                case DELETE:
                    requestBuilder.DELETE();
                    break;
                default:
                    throw new IllegalStateException("Operation not allowed");
            }

            return client.send(requestBuilder.build(), new BodyHandler<>(request.getResponseType()))
                    .body()
                    .get();

        } catch (URISyntaxException | IOException | InterruptedException | HttpResponseException e) {
            throw ErrorHandler.parseError(e);
        }
    }

    private static URI buildURI(String basePath, Map<String, List<String>> queryParams, List<String> pathParams) throws URISyntaxException {
        Object[] encodedPathParams = pathParams
                .stream()
                .map(RequestHandler::encodeURIComponent)
                .toArray();
        final URIBuilder uriBuilder = new URIBuilder(String.format(basePath, encodedPathParams));
        final List<NameValuePair> queryParamsExpanded = new ArrayList<>();
        //Iterate over query params list so we can add multiple query params with the same key
        queryParams.forEach((key, paramList) -> paramList
                .forEach(value -> queryParamsExpanded.add(new BasicNameValuePair(key, value))));
        uriBuilder.setParameters(queryParamsExpanded);
        return uriBuilder.build();
    }

    private static String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setNextRequestHeaders(Map<String, String> headers) {
        requestHeaders.set(headers);
    }

    public Map<String, String> getHeaders() {
        return requestHeaders.get();
    }
}