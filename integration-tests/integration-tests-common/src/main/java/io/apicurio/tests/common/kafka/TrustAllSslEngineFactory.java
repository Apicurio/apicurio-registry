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

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.network.Mode;
import org.apache.kafka.common.security.auth.SslEngineFactory;

/**
 * @author Fabian Martinez
 */
public class TrustAllSslEngineFactory implements SslEngineFactory {

    private SSLContext sslContext;

    @Override
    public void close() {
        this.sslContext = null;
    }

    @Override
    public SSLEngine createClientSslEngine(String peerHost, int peerPort, String endpointIdentification) {
        return createSslEngine(Mode.CLIENT, peerHost, peerPort, endpointIdentification);
    }

    @Override
    public SSLEngine createServerSslEngine(String peerHost, int peerPort) {
        return createSslEngine(Mode.SERVER, peerHost, peerPort, null);
    }

    /**
     * @see org.apache.kafka.common.security.auth.SslEngineFactory#shouldBeRebuilt(java.util.Map)
     */
    @Override
    public boolean shouldBeRebuilt(Map<String, Object> nextConfigs) {
        return false;
    }

    /**
     * @see org.apache.kafka.common.security.auth.SslEngineFactory#reconfigurableConfigs()
     */
    @Override
    public Set<String> reconfigurableConfigs() {
        return Collections.emptySet();
    }

    @Override
    public KeyStore keystore() {
        return null;
    }

    @Override
    public KeyStore truststore() {
        return null;
    }

    /**
     * @see org.apache.kafka.common.Configurable#configure(java.util.Map)
     */
    @Override
    public void configure(Map<String, ?> configs) {
        this.sslContext = createSSLContext();
    }

    private SSLEngine createSslEngine(Mode mode, String peerHost, int peerPort, String endpointIdentification) {
        SSLEngine sslEngine = sslContext.createSSLEngine(peerHost, peerPort);

        if (mode == Mode.SERVER) {
            sslEngine.setUseClientMode(false);
        } else {
            sslEngine.setUseClientMode(true);
            SSLParameters sslParams = sslEngine.getSSLParameters();
            // SSLParameters#setEndpointIdentificationAlgorithm enables endpoint validation
            // only in client mode. Hence, validation is enabled only for clients.
            sslParams.setEndpointIdentificationAlgorithm(endpointIdentification);
            sslEngine.setSSLParameters(sslParams);
        }
        return sslEngine;
    }

    private SSLContext createSSLContext() {
        try {

            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {

                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs,
                        String authType) {

                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs,
                        String authType) {

                }
            } };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new KafkaException(e);
        }
    }

}
