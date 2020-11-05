/*
 * Copyright 2020 Red Hat
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

import io.apicurio.registry.client.request.HeadersInterceptor;
import io.apicurio.registry.client.request.RequestHandler;
import io.apicurio.registry.client.service.ArtifactsService;
import io.apicurio.registry.client.service.IdsService;
import io.apicurio.registry.client.service.RulesService;
import io.apicurio.registry.client.service.SearchService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.IfExistsType;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_HEADERS_PREFIX;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_KEYSTORE_LOCATION;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_KEYSTORE_PASSWORD;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_KEYSTORE_TYPE;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_KEY_PASSWORD;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_LOCATION;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_PASSWORD;
import static io.apicurio.registry.client.request.RestClientConfig.REGISTRY_REQUEST_TRUSTSTORE_TYPE;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryRestClientImpl implements RegistryRestClient {

    private final RequestHandler requestHandler;
    private final OkHttpClient httpClient;

    private ArtifactsService artifactsService;
    private RulesService rulesService;
    private SearchService searchService;
    private IdsService idsService;

    RegistryRestClientImpl(String baseUrl) {
        this(baseUrl, Collections.emptyMap());
    }

    RegistryRestClientImpl(String baseUrl, Map<String, Object> config) {
        this(baseUrl, createHttpClientWithConfig(baseUrl, config));
    }

    RegistryRestClientImpl(String baseUrl, OkHttpClient okHttpClient) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        this.httpClient = okHttpClient;

        Retrofit retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .baseUrl(baseUrl)
                .build();

        this.requestHandler = new RequestHandler();

        initServices(retrofit);
    }

    private static OkHttpClient createHttpClientWithConfig(String baseUrl, Map<String, Object> configs) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        okHttpClientBuilder = addHeaders(okHttpClientBuilder, baseUrl, configs);
        okHttpClientBuilder = addSSL(okHttpClientBuilder, configs);
        return okHttpClientBuilder.build();
    }

    private static OkHttpClient.Builder addHeaders(OkHttpClient.Builder okHttpClientBuilder, String baseUrl, Map<String, Object> configs) {

        Map<String, String> requestHeaders = configs.entrySet().stream()
                .filter(map -> map.getKey().startsWith(REGISTRY_REQUEST_HEADERS_PREFIX))
                .collect(Collectors.toMap(map -> map.getKey()
                        .replace(REGISTRY_REQUEST_HEADERS_PREFIX, ""), map -> map.getValue().toString()));

        if (!requestHeaders.containsKey("Authorization")) {
            // Check if url includes user/password
            // and add auth header if it does
            HttpUrl url = HttpUrl.parse(baseUrl);
            String user = url.encodedUsername();
            String pwd = url.encodedPassword();
            if (user != null && !user.isEmpty()) {
                String credentials = Credentials.basic(user, pwd);
                requestHeaders.put("Authorization", credentials);
            }
        }

        if (!requestHeaders.isEmpty()) {
            final Interceptor headersInterceptor = new HeadersInterceptor(requestHeaders);
            return okHttpClientBuilder.addInterceptor(headersInterceptor);
        } else {
            return okHttpClientBuilder;
        }
    }

    private static OkHttpClient.Builder addSSL(OkHttpClient.Builder okHttpClientBuilder, Map<String, Object> configs) {

        try {
            KeyManager[] keyManagers = getKeyManagers(configs);
            TrustManager[] trustManagers = getTrustManagers(configs);

            if (trustManagers != null && (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager))) {
                throw new IllegalStateException("A single X509TrustManager is expected. Unexpected trust managers: " + Arrays.toString(trustManagers));
            }

            if (keyManagers != null || trustManagers != null) {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
                return okHttpClientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
            } else {
                return okHttpClientBuilder;
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

    private void initServices(Retrofit retrofit) {
        artifactsService = retrofit.create(ArtifactsService.class);
        rulesService = retrofit.create(RulesService.class);
        idsService = retrofit.create(IdsService.class);
        searchService = retrofit.create(SearchService.class);
    }

    @Override
    public List<String> listArtifacts() {

        return requestHandler.execute(artifactsService.listArtifacts());
    }

    @Override
    public ArtifactMetaData createArtifact(InputStream data) {
        return this.createArtifact(null, null, data);
    }

    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        return this.createArtifact(artifactId, artifactType, data, null, null);
    }
    
    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data,
            IfExistsType ifExists, Boolean canonical) {
        return requestHandler.execute(artifactsService.createArtifact(artifactType, artifactId, ifExists, canonical, 
                RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getLatestArtifact(String artifactId) {

        return requestHandler.execute(artifactsService.getLatestArtifact(artifactId)).byteStream();
    }

    @Override
    public ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {

        return requestHandler.execute(artifactsService.updateArtifact(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public void deleteArtifact(String artifactId) {

        requestHandler.execute(artifactsService.deleteArtifact(artifactId));
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {

        requestHandler.execute(artifactsService.updateArtifactState(artifactId, data));
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {

        return requestHandler.execute(artifactsService.getArtifactMetaData(artifactId));
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {

        requestHandler.execute(artifactsService.updateArtifactMetaData(artifactId, data));
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, Boolean canonical, InputStream data) {
        return requestHandler.execute(artifactsService.getArtifactMetaDataByContent(artifactId, canonical, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {

        return requestHandler.execute(artifactsService.listArtifactVersions(artifactId));
    }

    @Override
    public VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data) {

        return requestHandler.execute(artifactsService.createArtifactVersion(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getArtifactVersion(String artifactId, Integer version) {

        return requestHandler.execute(artifactsService.getArtifactVersion(version, artifactId)).byteStream();
    }

    @Override
    public void updateArtifactVersionState(String artifactId, Integer version, UpdateState data) {

        requestHandler.execute(artifactsService.updateArtifactVersionState(version, artifactId, data));
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) {

        return requestHandler.execute(artifactsService.getArtifactVersionMetaData(version, artifactId));
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData data) {

        requestHandler.execute(artifactsService.updateArtifactVersionMetaData(version, artifactId, data));
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {

        requestHandler.execute(artifactsService.deleteArtifactVersionMetaData(version, artifactId));
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {

        return requestHandler.execute(artifactsService.listArtifactRules(artifactId));
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {

        requestHandler.execute(artifactsService.createArtifactRule(artifactId, data));
    }

    @Override
    public void deleteArtifactRules(String artifactId) {

        requestHandler.execute(artifactsService.deleteArtifactRules(artifactId));
    }

    @Override
    public Rule getArtifactRuleConfig(String artifactId, RuleType rule) {

        return requestHandler.execute(artifactsService.getArtifactRuleConfig(rule, artifactId));
    }

    @Override
    public Rule updateArtifactRuleConfig(String artifactId, RuleType rule, Rule data) {

        return requestHandler.execute(artifactsService.updateArtifactRuleConfig(rule, artifactId, data));
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) {

        requestHandler.execute(artifactsService.deleteArtifactRule(rule, artifactId));
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {

        requestHandler.execute(artifactsService.testUpdateArtifact(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getArtifactByGlobalId(long globalId) {

        return requestHandler.execute(idsService.getArtifactByGlobalId(globalId)).byteStream();
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {

        return requestHandler.execute(idsService.getArtifactMetaDataByGlobalId(globalId));
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit) {

        return requestHandler.execute(searchService.searchArtifacts(search, offset, limit, over, order));
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {

        return requestHandler.execute(searchService.searchVersions(artifactId, offset, limit));
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {

        return requestHandler.execute(rulesService.getGlobalRuleConfig(rule));
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {

        return requestHandler.execute(rulesService.updateGlobalRuleConfig(rule, data));
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {

        requestHandler.execute(rulesService.deleteGlobalRule(rule));
    }

    @Override
    public List<RuleType> listGlobalRules() {

        return requestHandler.execute(rulesService.listGlobalRules());
    }

    @Override
    public void createGlobalRule(Rule data) {

        requestHandler.execute(rulesService.createGlobalRule(data));
    }

    @Override
    public void deleteAllGlobalRules() {

        requestHandler.execute(rulesService.deleteAllGlobalRules());
    }

    @Override
    public void close() throws Exception {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            httpClient.cache().close();
        }
    }
}
