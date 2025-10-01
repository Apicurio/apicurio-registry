package io.apicurio.registry.maven;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
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
        return RegistryClientFactory.create(clientOptions.retry());
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
