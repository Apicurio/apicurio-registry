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

package io.apicurio.tests.common.kafka;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.kafka.common.security.auth.SslEngineFactory;

/**
 * @author Fabian Martinez
 */
public class TrustAllSslEngineFactory implements SslEngineFactory {

    @Override
    public SSLEngine createClientSslEngine(String peerHost, int peerPort, String endpointIdentification) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() { return null; }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }};
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLEngine sslEngine = sc.createSSLEngine(peerHost, peerPort);
            sslEngine.setUseClientMode(true);
            return sslEngine;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SSLEngine createServerSslEngine(String peerHost, int peerPort) {
        return null;
    }

    @Override
    public boolean shouldBeRebuilt(Map<String, Object> nextConfigs) {
        return false;
    }

    @Override
    public Set<String> reconfigurableConfigs() {
        return null;
    }

    @Override
    public KeyStore keystore() {
        return null;
    }

    @Override
    public KeyStore truststore() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void configure(Map<String, ?> configs) {

    }

}
