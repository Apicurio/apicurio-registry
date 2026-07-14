package io.apicurio.registry.resolver.client;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.client.common.HttpAdapterType;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.vertx.core.Vertx;

import java.util.logging.Logger;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;

/**
 * Factory used to create instances of {@link RegistryClientFacade}.  Typically the
 * factory will create the correct implementation of the interface based on the
 * endpoint URL of the Registry Core API configured.  For example, if the URL contains
 * "/apis/v2", then a v2 implementation will be created.
 *
 */
public class RegistryClientFacadeFactory {

    private static final Logger logger = Logger.getLogger(RegistryClientFacadeFactory.class.getSimpleName());
    private static final String CLASSPATH_PREFIX = "classpath:";

    public static RegistryClientFacade create(SchemaResolverConfig config) {
        String baseUrl = config.getRegistryUrl();
        if (baseUrl == null) {
            throw new IllegalArgumentException(
                    "Missing registry base url, set " + SchemaResolverConfig.REGISTRY_URL);
        }

        Vertx vertx = config.getVertx();

        String endpointVersion = null;
        if (config.getRegistryUrlVersion() != null) {
            endpointVersion = config.getRegistryUrlVersion();
        }
        if (endpointVersion == null && baseUrl.contains("/apis/registry/v2")) {
            endpointVersion = "2";
        } else {
            endpointVersion = "3";
        }

        switch  (endpointVersion) {
            case "2":
                return create_v2(config, vertx);
            case "3":
            default:
                return create_v3(config, vertx);
        }
    }

    static RegistryClientOptions buildClientOptions(SchemaResolverConfig config, Vertx vertx) {
        final String baseUrl = config.getRegistryUrl();
        final String httpAdapterStr = config.getHttpAdapter();
        HttpAdapterType httpAdapterType = parseHttpAdapterType(httpAdapterStr);

        RegistryClientOptions clientOptions = createClientOptions(baseUrl, httpAdapterType, vertx);

        configureAuthentication(clientOptions, config);

        clientOptions.retry();

        configureTrustStore(clientOptions, config);
        configureKeyStore(clientOptions, config);
        configureProxy(clientOptions, config);

        if (config.isOtelEnabled()) {
            clientOptions.enableOpenTelemetry();
        }

        return clientOptions;
    }

    private static RegistryClientOptions createClientOptions(String baseUrl, HttpAdapterType httpAdapterType, Vertx vertx) {
        if (httpAdapterType == HttpAdapterType.JDK) {
            return RegistryClientOptions.create(baseUrl).httpAdapter(HttpAdapterType.JDK);
        } else if (httpAdapterType == HttpAdapterType.VERTX) {
            return RegistryClientOptions.create(baseUrl, vertx).httpAdapter(HttpAdapterType.VERTX);
        } else {
            return RegistryClientOptions.create(baseUrl, vertx);
        }
    }

    private static void configureAuthentication(RegistryClientOptions clientOptions, SchemaResolverConfig config) {
        final String tokenEndpoint = config.getTokenEndpoint();
        final String username = config.getAuthUsername();
        try {
            if (tokenEndpoint != null) {
                final String clientId = config.getAuthClientId();
                final String clientSecret = config.getAuthClientSecret();
                final String clientScope = config.getAuthClientScope();

                if (clientId == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth clientId, set " + SchemaResolverConfig.AUTH_CLIENT_ID);
                }

                if (clientSecret == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth secret, set " + SchemaResolverConfig.AUTH_CLIENT_SECRET);
                }

                clientOptions.oauth2(tokenEndpoint, clientId, clientSecret, clientScope);
            } else if (username != null) {
                final String password = config.getAuthPassword();

                if (password == null) {
                    throw new IllegalArgumentException(
                            "Missing registry auth password, set " + SchemaResolverConfig.AUTH_PASSWORD);
                }

                clientOptions.basicAuth(username, password);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void configureTrustStore(RegistryClientOptions clientOptions, SchemaResolverConfig config) {
        boolean trustAll = config.getTlsTrustAll();
        if (trustAll) {
            clientOptions.trustAll(true);
            return;
        }

        String truststoreLocation = config.getTlsTruststoreLocation();
        if (truststoreLocation != null) {
            configureTrustStoreFile(clientOptions, truststoreLocation, config.getTlsTruststorePassword(), config.getTlsTruststoreType());
        } else {
            String certificates = config.getTlsCertificates();
            if (certificates != null) {
                configureTrustStoreCertificates(clientOptions, certificates);
            }
        }

        if (!config.getTlsVerifyHost()) {
            clientOptions.verifyHost(false);
        }
    }

    private static void configureTrustStoreFile(RegistryClientOptions clientOptions, String location, String password, String type) {
        String resolvedLocation = resolveClasspathResourceToTempFile(location);
        if ("JKS".equalsIgnoreCase(type)) {
            clientOptions.trustStoreJks(resolvedLocation, password);
        } else if ("PKCS12".equalsIgnoreCase(type) || "P12".equalsIgnoreCase(type)) {
            clientOptions.trustStorePkcs12(resolvedLocation, password);
        } else if ("PEM".equalsIgnoreCase(type)) {
            clientOptions.trustStorePem(resolvedLocation);
        } else {
            throw new IllegalArgumentException("Unsupported TLS truststore type: " + type + ". Supported types are: JKS, PKCS12, PEM");
        }
    }

    private static void configureTrustStoreCertificates(RegistryClientOptions clientOptions, String certificates) {
        if (certificates.contains("-----BEGIN CERTIFICATE-----")) {
            clientOptions.trustStorePemContent(certificates);
        } else {
            String[] certPaths = certificates.split(",");
            for (int i = 0; i < certPaths.length; i++) {
                certPaths[i] = certPaths[i].trim();
                if (!isFilePath(certPaths[i])) {
                    throw new IllegalArgumentException("Invalid truststore certificate path: " + certPaths[i] + ". File does not exist.");
                }
                certPaths[i] = resolveClasspathResourceToTempFile(certPaths[i]);
            }
            clientOptions.trustStorePem(certPaths);
        }
    }

    private static void configureKeyStore(RegistryClientOptions clientOptions, SchemaResolverConfig config) {
        String keystoreLocation = config.getTlsKeystoreLocation();
        String clientCert = config.getTlsClientCertificate();
        String clientKey = config.getTlsClientKey();

        if (keystoreLocation != null) {
            configureKeyStoreFile(clientOptions, keystoreLocation, config.getTlsKeystorePassword(), config.getTlsKeystoreType(), clientKey);
        } else if (clientCert != null && clientKey != null) {
            configureKeyStorePem(clientOptions, clientCert, clientKey);
        }
    }

    private static void configureKeyStoreFile(RegistryClientOptions clientOptions, String location, String password, String type, String clientKey) {
        String resolvedLocation = resolveClasspathResourceToTempFile(location);
        String resolvedClientKey = resolveClasspathResourceToTempFile(clientKey);
        if ("JKS".equalsIgnoreCase(type)) {
            clientOptions.keystoreJks(resolvedLocation, password);
        } else if ("PKCS12".equalsIgnoreCase(type) || "P12".equalsIgnoreCase(type)) {
            clientOptions.keystorePkcs12(resolvedLocation, password);
        } else if ("PEM".equalsIgnoreCase(type)) {
            if (resolvedClientKey == null) {
                throw new IllegalArgumentException("TLS client key is required when TLS keystore type is PEM");
            }
            clientOptions.keystorePem(resolvedLocation, resolvedClientKey);
        } else {
            throw new IllegalArgumentException("Unsupported TLS keystore type: " + type + ". Supported types are: JKS, PKCS12, PEM");
        }
    }

    private static void configureKeyStorePem(RegistryClientOptions clientOptions, String clientCert, String clientKey) {
        boolean isCertPath = isFilePath(clientCert);
        boolean isKeyPath = isFilePath(clientKey);

        boolean isCertContent = !isCertPath && clientCert.contains("-----BEGIN CERTIFICATE-----");
        boolean isKeyContent = !isKeyPath && isPemKeyContent(clientKey);

        validatePemConfig(isCertPath, isCertContent, "certificate");
        validatePemConfig(isKeyPath, isKeyContent, "key");

        if (isCertContent && isKeyContent) {
            clientOptions.keystorePemContent(clientCert, clientKey);
        } else if (isCertPath && isKeyPath) {
            String resolvedCert = resolveClasspathResourceToTempFile(clientCert);
            String resolvedKey = resolveClasspathResourceToTempFile(clientKey);
            clientOptions.keystorePem(resolvedCert, resolvedKey);
        } else {
            String certContent = isCertContent ? clientCert : readPemFile(clientCert);
            String keyContent = isKeyContent ? clientKey : readPemFile(clientKey);
            clientOptions.keystorePemContent(certContent, keyContent);
        }
    }

    private static String resolveClasspathResourceToTempFile(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return pathStr;
        }
        checkPathTraversal(pathStr);
        if (pathStr.startsWith(CLASSPATH_PREFIX)) {
            String resourcePath = pathStr.substring(CLASSPATH_PREFIX.length());
            try (var is = RegistryClientFacadeFactory.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
                }
                String prefix = "apicurio-tls-";
                String suffix = ".tmp";
                int lastDot = resourcePath.lastIndexOf('.');
                if (lastDot != -1) {
                    suffix = resourcePath.substring(lastDot);
                }
                Path parentDir = createSecureTempDirectory();
                Path tempFile = Files.createTempFile(parentDir, prefix, suffix);
                tempFile.toFile().deleteOnExit();
                try (var os = Files.newOutputStream(tempFile)) {
                    is.transferTo(os);
                }
                return tempFile.toAbsolutePath().toString();
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to load classpath resource: " + pathStr, e);
            }
        }
        return pathStr;
    }

    private static Path createSecureTempDirectory() throws java.io.IOException {
        Path parentDir;
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            parentDir = Paths.get(userHome).resolve(".apicurio-tls");
        } else {
            parentDir = Paths.get(".").resolve(".apicurio-tls");
        }
        Files.createDirectories(parentDir);
        setDirectoryPermissions(parentDir);
        return parentDir;
    }

    private static void setDirectoryPermissions(Path parentDir) throws java.io.IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(parentDir, PosixFilePermissions.fromString("rwx------"));
        } else {
            File dirFile = parentDir.toFile();
            boolean readable = dirFile.setReadable(true, true);
            boolean writable = dirFile.setWritable(true, true);
            boolean executable = dirFile.setExecutable(true, true);
            if (!readable || !writable || !executable) {
                throw new java.io.IOException("Failed to set secure permissions on temp directory: " + parentDir);
            }
        }
    }

    private static boolean isPemKeyContent(String clientKey) {
        return clientKey.contains("-----BEGIN ") && clientKey.contains(" PRIVATE KEY-----");
    }

    private static void validatePemConfig(boolean isPath, boolean isContent, String name) {
        if (!isPath && !isContent) {
            throw new IllegalArgumentException("Invalid client " + name + " configuration: value is not an existing file path and does not contain PEM " + name + " header");
        }
    }

    private static void configureProxy(RegistryClientOptions clientOptions, SchemaResolverConfig config) {
        String proxyHost = config.getProxyHost();
        Integer proxyPort = config.getProxyPort();

        if (proxyHost != null && proxyPort != null) {
            clientOptions.proxy(proxyHost, proxyPort);

            String proxyUsername = config.getProxyUsername();
            String proxyPassword = config.getProxyPassword();

            if (proxyUsername != null && proxyPassword != null) {
                clientOptions.proxyAuth(proxyUsername, proxyPassword);
            }
        }
    }

    private static RegistryClientFacade create_v3(SchemaResolverConfig config, Vertx vertx) {
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl(client, config.getRegistryUrl(), config.getUsageTelemetryClientId());
    }

    private static RegistryClientFacade create_v2(SchemaResolverConfig config, Vertx vertx) {
        logger.warning("Using a deprecated version (2.x) of Apicurio Registry.  It is recommended to upgrade your Apicurio Registry to version 3.");
        RegistryClientOptions clientOptions = buildClientOptions(config, vertx);
        var client = RegistryV2ClientFactory.create(clientOptions);
        return new RegistryClientFacadeImpl_v2(client);
    }

    private static HttpAdapterType parseHttpAdapterType(String httpAdapterStr) {
        if (httpAdapterStr == null) {
            return HttpAdapterType.AUTO;
        }
        switch (httpAdapterStr.toUpperCase()) {
            case "JDK":
                return HttpAdapterType.JDK;
            case "VERTX":
                return HttpAdapterType.VERTX;
            case "AUTO":
            default:
                return HttpAdapterType.AUTO;
        }
    }

    private static void checkPathTraversal(String pathStr) {
        String cleanPath = pathStr.startsWith(CLASSPATH_PREFIX) ? pathStr.substring(CLASSPATH_PREFIX.length()) : pathStr;
        Path path = Paths.get(cleanPath);
        for (Path element : path) {
            if ("..".equals(element.toString())) {
                throw new IllegalArgumentException("Path traversal detected: " + pathStr);
            }
        }
    }

    private static boolean isFilePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        checkPathTraversal(value);
        if (value.startsWith(CLASSPATH_PREFIX)) {
            String resourcePath = value.substring(CLASSPATH_PREFIX.length());
            try {
                return RegistryClientFacadeFactory.class.getClassLoader().getResource(resourcePath) != null;
            } catch (Exception e) {
                return false;
            }
        }
        try {
            Path path = Paths.get(value).normalize();
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    private static String readPemFile(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            throw new IllegalArgumentException("PEM file path cannot be empty");
        }
        checkPathTraversal(pathStr);
        try {
            String content;
            if (pathStr.startsWith(CLASSPATH_PREFIX)) {
                String resourcePath = pathStr.substring(CLASSPATH_PREFIX.length());
                try (var is = RegistryClientFacadeFactory.class.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        throw new IllegalArgumentException("Classpath resource not found: " + resourcePath);
                    }
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                content = Files.readString(Paths.get(pathStr).normalize(), StandardCharsets.UTF_8);
            }

            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("PEM content is empty for path: " + pathStr);
            }
            return content;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read PEM file from path: " + pathStr, e);
        }
    }
}
