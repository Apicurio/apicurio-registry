package io.apicurio.registry.services;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.runtime.StartupEvent;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_HTTP;

@Singleton
public class HttpSslConfig {

    private static final Logger log = LoggerFactory.getLogger(HttpSslConfig.class);

    @ConfigProperty(name = "apicurio.http.ssl.protocols")
    @Info(category = CATEGORY_HTTP,
          description = "Comma-separated list of enabled SSL/TLS protocol versions "
                      + "(e.g. TLSv1.2,TLSv1.3). When not set, the JVM/Quarkus defaults apply. "
                      + "Invalid values will cause TLS handshake failures at runtime.",
          availableSince = "3.3.1")
    Optional<String> sslProtocols;

    @ConfigProperty(name = "apicurio.http.ssl.cipher-suites")
    @Info(category = CATEGORY_HTTP,
          description = "Comma-separated list of enabled cipher suites "
                      + "(e.g. TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256). "
                      + "When not set, the JVM/Quarkus defaults apply. "
                      + "Invalid values will cause TLS handshake failures at runtime.",
          availableSince = "3.3.1")
    Optional<String> sslCipherSuites;

    void onStartup(@Observes StartupEvent event) {
        try {
            SSLParameters supported = SSLContext.getDefault().getSupportedSSLParameters();
            Set<String> supportedProtocols = Set.of(supported.getProtocols());
            Set<String> supportedCiphers = Set.of(supported.getCipherSuites());

            sslProtocols.ifPresent(protocols -> {
                Set<String> configured = Arrays.stream(protocols.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                configured.stream()
                        .filter(p -> !supportedProtocols.contains(p))
                        .forEach(p -> log.warn(
                                "Configured SSL protocol '{}' is not recognized by this JVM. "
                                + "This will likely cause TLS handshake failures.", p));
            });

            sslCipherSuites.ifPresent(ciphers -> {
                Set<String> configured = Arrays.stream(ciphers.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                configured.stream()
                        .filter(c -> !supportedCiphers.contains(c))
                        .forEach(c -> log.warn(
                                "Configured cipher suite '{}' is not recognized by this JVM. "
                                + "This will likely cause TLS handshake failures.", c));
            });
        } catch (NoSuchAlgorithmException e) {
            log.warn("Unable to validate SSL/TLS configuration: {}", e.getMessage());
        }
    }
}
