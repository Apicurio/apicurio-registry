package io.apicurio.registry.services;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_HTTP;

@Singleton
public class HttpSslConfig {

    @ConfigProperty(name = "apicurio.http.ssl.protocols")
    @Info(category = CATEGORY_HTTP,
          description = "Comma-separated list of enabled SSL/TLS protocol versions "
                      + "(e.g. TLSv1.2,TLSv1.3). When not set, the JVM/Quarkus defaults apply.",
          availableSince = "3.3.1")
    Optional<String> sslProtocols;

    @ConfigProperty(name = "apicurio.http.ssl.cipher-suites")
    @Info(category = CATEGORY_HTTP,
          description = "Comma-separated list of enabled cipher suites "
                      + "(e.g. TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256). "
                      + "When not set, the JVM/Quarkus defaults apply.",
          availableSince = "3.3.1")
    Optional<String> sslCipherSuites;
}
