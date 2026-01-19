package io.apicurio.registry.maven;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.types.ContentTypes;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Base class for all Registry Mojo's. It handles RegistryService's (aka client) lifecycle.
 */
public abstract class AbstractRegistryMojo extends AbstractMojo {

    /**
     * The registry's url. e.g. http://localhost:8080/apis/registry/v3
     */
    @Parameter(required = true, property = "apicurio.url")
    String registryUrl;

    /**
     * The URL of the authentication server (if required).
     */
    @Parameter(property = "auth.server.url")
    String authServerUrl;

    /**
     * The client id to use when authenticating to the auth sever.
     */
    @Parameter(property = "client.id")
    String clientId;

    /**
     * The client secret to use when authenticating to the auth sever.
     */
    @Parameter(property = "client.secret")
    String clientSecret;

    /**
     * The client scope to use when authenticating to the auth sever.
     */
    @Parameter(property = "client.scope")
    String clientScope;

    /**
     * Authentication credentials: username
     */
    @Parameter(property = "username")
    String username;

    /**
     * Authentication credentials: password
     */
    @Parameter(property = "password")
    String password;

    /**
     * Path to the trust store file for SSL/TLS connections.
     * Supports JKS, PKCS12, and PEM formats. The format is auto-detected from the file extension
     * (.jks, .p12/.pfx, .pem/.crt/.cer) unless explicitly specified via trustStoreType.
     */
    @Parameter(property = "trustStorePath")
    String trustStorePath;

    /**
     * Password for the trust store (required for JKS and PKCS12 formats).
     */
    @Parameter(property = "trustStorePassword")
    String trustStorePassword;

    /**
     * Type of the trust store: JKS, PKCS12, or PEM. If not specified, the type is auto-detected
     * from the file extension.
     */
    @Parameter(property = "trustStoreType")
    String trustStoreType;

    /**
     * Path to the key store file for mutual TLS (mTLS) client authentication.
     * Supports JKS, PKCS12, and PEM formats. The format is auto-detected from the file extension
     * (.jks, .p12/.pfx, .pem/.crt/.cer) unless explicitly specified via keyStoreType.
     */
    @Parameter(property = "keyStorePath")
    String keyStorePath;

    /**
     * Password for the key store (required for JKS and PKCS12 formats).
     */
    @Parameter(property = "keyStorePassword")
    String keyStorePassword;

    /**
     * Type of the key store: JKS, PKCS12, or PEM. If not specified, the type is auto-detected
     * from the file extension.
     */
    @Parameter(property = "keyStoreType")
    String keyStoreType;

    /**
     * Path to the PEM private key file (required when using PEM format for mTLS and keyStorePath
     * points to the certificate file).
     */
    @Parameter(property = "keyStorePemKeyPath")
    String keyStorePemKeyPath;

    /**
     * If set to true, trust all SSL/TLS certificates without validation.
     * WARNING: This should only be used for development/testing purposes.
     */
    @Parameter(property = "trustAll", defaultValue = "false")
    boolean trustAll;

    /**
     * If set to false, disable hostname verification in SSL/TLS connections.
     * WARNING: Disabling this reduces security and should only be used for development/testing.
     */
    @Parameter(property = "verifyHostname", defaultValue = "true")
    boolean verifyHostname;

    protected Vertx createVertx() {
        var options = new VertxOptions();
        var fsOpts = new FileSystemOptions();
        fsOpts.setFileCachingEnabled(false);
        fsOpts.setClassPathResolvingEnabled(false);
        options.setFileSystemOptions(fsOpts);
        return Vertx.vertx(options);
    }

    protected RegistryClient createClient(Vertx vertx) {
        RegistryClientOptions clientOptions = RegistryClientOptions.create(registryUrl, vertx);

        // Configure authentication
        if (authServerUrl != null && clientId != null && clientSecret != null) {
            if (clientScope != null && !clientScope.isEmpty()) {
                getLog().info("Creating registry client with OAuth2 authentication with scope.");
                clientOptions.oauth2(authServerUrl, clientId, clientSecret, clientScope);
            } else {
                getLog().info("Creating registry client with OAuth2 authentication.");
                clientOptions.oauth2(authServerUrl, clientId, clientSecret);
            }
        } else if (username != null && password != null) {
            getLog().info("Creating registry client with Basic authentication.");
            clientOptions.basicAuth(username, password);
        } else {
            getLog().info("Creating registry client without authentication.");
        }

        // Configure TLS/SSL trust store
        configureTrustStore(clientOptions);

        // Configure TLS/SSL key store for mTLS
        configureKeyStore(clientOptions);

        // Configure additional SSL options
        if (trustAll) {
            getLog().warn("TLS trust-all mode enabled. This is insecure and should only be used for development/testing.");
            clientOptions.trustAll(true);
        }
        if (!verifyHostname) {
            getLog().warn("Hostname verification disabled. This reduces security and should only be used for development/testing.");
            clientOptions.verifyHost(false);
        }

        return RegistryClientFactory.create(clientOptions.retry());
    }

    // Package-private for testing
    void configureTrustStore(RegistryClientOptions clientOptions) {
        if (trustStorePath == null || trustStorePath.isEmpty()) {
            return;
        }

        String detectedType = detectStoreType(trustStorePath, trustStoreType);
        getLog().info("Configuring trust store: " + trustStorePath + " (type: " + detectedType + ")");

        switch (detectedType.toUpperCase(Locale.ROOT)) {
            case "JKS":
                clientOptions.trustStoreJks(trustStorePath, trustStorePassword);
                break;
            case "PKCS12":
                clientOptions.trustStorePkcs12(trustStorePath, trustStorePassword);
                break;
            case "PEM":
                clientOptions.trustStorePem(trustStorePath);
                break;
            default:
                getLog().warn("Unknown trust store type: " + detectedType + ". Attempting to use as PKCS12.");
                clientOptions.trustStorePkcs12(trustStorePath, trustStorePassword);
        }
    }

    // Package-private for testing
    void configureKeyStore(RegistryClientOptions clientOptions) {
        if (keyStorePath == null || keyStorePath.isEmpty()) {
            return;
        }

        String detectedType = detectStoreType(keyStorePath, keyStoreType);
        getLog().info("Configuring key store for mTLS: " + keyStorePath + " (type: " + detectedType + ")");

        switch (detectedType.toUpperCase(Locale.ROOT)) {
            case "JKS":
                clientOptions.keystoreJks(keyStorePath, keyStorePassword);
                break;
            case "PKCS12":
                clientOptions.keystorePkcs12(keyStorePath, keyStorePassword);
                break;
            case "PEM":
                if (keyStorePemKeyPath == null || keyStorePemKeyPath.isEmpty()) {
                    throw new IllegalArgumentException(
                            "keyStorePemKeyPath is required when using PEM format for mTLS. " +
                            "Set keyStorePath to the certificate file and keyStorePemKeyPath to the private key file.");
                }
                clientOptions.keystorePem(keyStorePath, keyStorePemKeyPath);
                break;
            default:
                getLog().warn("Unknown key store type: " + detectedType + ". Attempting to use as PKCS12.");
                clientOptions.keystorePkcs12(keyStorePath, keyStorePassword);
        }
    }

    // Package-private for testing
    String detectStoreType(String path, String explicitType) {
        if (explicitType != null && !explicitType.isEmpty()) {
            return explicitType;
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".jks")) {
            return "JKS";
        } else if (lowerPath.endsWith(".p12") || lowerPath.endsWith(".pfx")) {
            return "PKCS12";
        } else if (lowerPath.endsWith(".pem") || lowerPath.endsWith(".crt") || lowerPath.endsWith(".cer")) {
            return "PEM";
        }
        return "PKCS12"; // Default to PKCS12
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeInternal();
        } catch (ExecutionException e) {
            throw new MojoExecutionException(e);
        } catch (InterruptedException e) {
            throw new MojoFailureException(e);
        }
        closeClients();
    }

    private void closeClients() {
        // TODO: check there are no connection leaks etc...
    }

    protected abstract void executeInternal()
            throws MojoExecutionException, MojoFailureException, ExecutionException, InterruptedException;

    protected String getContentTypeByExtension(String fileName) {
        if (fileName == null)
            return null;
        String[] temp = fileName.split("[.]");
        String extension = temp[temp.length - 1];
        switch (extension.toLowerCase(Locale.ROOT)) {
            case "avro":
            case "avsc":
            case "json":
                return ContentTypes.APPLICATION_JSON;
            case "yml":
            case "yaml":
                return ContentTypes.APPLICATION_YAML;
            case "graphql":
                return ContentTypes.APPLICATION_GRAPHQL;
            case "proto":
                return ContentTypes.APPLICATION_PROTOBUF;
            case "wsdl":
            case "xsd":
            case "xml":
                return ContentTypes.APPLICATION_XML;
        }
        return null;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public void setAuthServerUrl(String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setClientScope(String clientScope) {
        this.clientScope = clientScope;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public void setKeyStorePemKeyPath(String keyStorePemKeyPath) {
        this.keyStorePemKeyPath = keyStorePemKeyPath;
    }

    public void setTrustAll(boolean trustAll) {
        this.trustAll = trustAll;
    }

    public void setVerifyHostname(boolean verifyHostname) {
        this.verifyHostname = verifyHostname;
    }

    protected void logAndThrow(ApiException e) throws MojoExecutionException, MojoFailureException {
        if (e instanceof RuleViolationProblemDetails) {
            logAndThrow((RuleViolationProblemDetails) e);
        }
        if (e instanceof ProblemDetails) {
            logAndThrow((ProblemDetails) e);
        }
    }

    protected void logAndThrow(ProblemDetails e) throws MojoExecutionException {
        getLog().error("---");
        getLog().error("Error registering artifact: " + e.getName());
        getLog().error(e.getTitle());
        getLog().error(e.getDetail());
        getLog().error("---");
        throw new MojoExecutionException("Error registering artifact: " + e.getName(), e);
    }

    protected void logAndThrow(RuleViolationProblemDetails e) throws MojoFailureException {
        getLog().error("---");
        getLog().error("Registry rule validation failure: " + e.getName());
        getLog().error(e.getTitle());
        if (e.getCauses() != null) {
            e.getCauses().forEach(cause -> {
                getLog().error("\t-> " + cause.getContext());
                getLog().error("\t   " + cause.getDescription());
            });
        }
        getLog().error("---");
        throw new MojoFailureException("Registry rule validation failure: " + e.getName(), e);
    }

}
