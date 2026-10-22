package io.apicurio.registry.utils.export;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class FakeHostnameVerifier implements HostnameVerifier {
    @Override
    public boolean verify(String s, SSLSession sslSession) {
        return true;
    }
}
