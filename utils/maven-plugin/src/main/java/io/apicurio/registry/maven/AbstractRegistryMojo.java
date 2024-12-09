package io.apicurio.registry.maven;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.types.ContentTypes;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.ext.web.client.WebClient;
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
     * The registry's url. e.g. http://localhost:8080/api/v3
     */
    @Parameter(required = true, property = "apicurio.url")
    String registryUrl;

    @Parameter(property = "auth.server.url")
    String authServerUrl;

    @Parameter(property = "client.id")
    String clientId;

    @Parameter(property = "client.secret")
    String clientSecret;

    @Parameter(property = "client.scope")
    String clientScope;

    @Parameter(property = "username")
    String username;

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
        WebClient provider = null;
        if (authServerUrl != null && clientId != null && clientSecret != null) {
            provider = VertXAuthFactory.buildOIDCWebClient(vertx, authServerUrl, clientId, clientSecret,
                    clientScope);
        } else if (username != null && password != null) {
            provider = VertXAuthFactory.buildSimpleAuthWebClient(vertx, username, password);
        } else {
            provider = WebClient.create(vertx);
        }

        var adapter = new VertXRequestAdapter(provider);
        adapter.setBaseUrl(registryUrl);
        return new RegistryClient(adapter);
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
