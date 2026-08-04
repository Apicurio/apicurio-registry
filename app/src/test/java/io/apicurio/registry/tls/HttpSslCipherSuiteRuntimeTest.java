package io.apicurio.registry.tls;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@QuarkusTest
@TestProfile(HttpSslCipherSuiteTestProfile.class)
public class HttpSslCipherSuiteRuntimeTest {

    @TestHTTPResource(value = "/apis", tls = true)
    URL httpsUrl;

    @Test
    public void testCipherSuiteRestrictionAtRuntime() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        String host = httpsUrl.getHost();
        int port = httpsUrl.getPort();

        // 1. Connection with the allowed cipher (TLS_AES_256_GCM_SHA384) should succeed
        SSLContext allowedCtx = SSLContext.getInstance("TLSv1.3");
        allowedCtx.init(null, trustAllCerts, new SecureRandom());
        SSLSocketFactory allowedFactory = allowedCtx.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) allowedFactory.createSocket(host, port)) {
            socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            socket.setEnabledCipherSuites(new String[]{"TLS_AES_256_GCM_SHA384"});
            socket.startHandshake();
            Assertions.assertTrue(socket.getSession().isValid());
            Assertions.assertEquals("TLS_AES_256_GCM_SHA384",
                    socket.getSession().getCipherSuite());
        }

        // 2. Connection with a disallowed cipher (TLS_AES_128_GCM_SHA256) should fail
        SSLContext disallowedCtx = SSLContext.getInstance("TLSv1.3");
        disallowedCtx.init(null, trustAllCerts, new SecureRandom());
        SSLSocketFactory disallowedFactory = disallowedCtx.getSocketFactory();
        Assertions.assertThrows(IOException.class, () -> {
            try (SSLSocket socket = (SSLSocket) disallowedFactory.createSocket(host, port)) {
                socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                socket.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256"});
                socket.startHandshake();
            }
        });
    }
}
