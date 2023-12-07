package io.apicurio.registry.maven;

import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.authentication.AuthenticationProvider;
import com.microsoft.kiota.authentication.BaseBearerTokenAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.registry.auth.BasicAuthenticationProvider;
import io.apicurio.registry.auth.OidcAccessTokenProvider;
import io.apicurio.registry.types.ContentTypes;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import io.apicurio.registry.rest.client.RegistryClient;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Base class for all Registry Mojo's.
 * It handles RegistryService's (aka client) lifecycle.
 *
 */
public abstract class AbstractRegistryMojo extends AbstractMojo {

    /**
     * The registry's url.
     * e.g. http://localhost:8080/api/v3
     */
    @Parameter(required = true, property = "registry.url")
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

    private RegistryClient client;

    protected RegistryClient getClient() {
        if (client == null) {
            AuthenticationProvider provider = null;
            if (authServerUrl != null && clientId != null && clientSecret != null) {
                provider = new BaseBearerTokenAuthenticationProvider(new OidcAccessTokenProvider(authServerUrl, clientId, clientSecret, null, clientScope));
            } else if (username != null && password != null) {
                provider = new BasicAuthenticationProvider(username, password);
            } else {
                provider = new AnonymousAuthenticationProvider();
            }

            var adapter = new OkHttpRequestAdapter(provider);
            adapter.setBaseUrl(registryUrl);
            client = new RegistryClient(adapter);
        }
        return client;
    }

    public void setClient(RegistryClient client) {
        this.client = client;
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

    protected abstract void executeInternal() throws MojoExecutionException, MojoFailureException, ExecutionException, InterruptedException;

    protected String getContentTypeByExtension(String fileName){
        if(fileName == null) return null;
        String[] temp = fileName.split("[.]");
        String extension = temp[temp.length - 1];
        switch (extension.toLowerCase(Locale.ROOT)){
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

    public void setClientScope(String clientScope) { this.clientScope = clientScope; }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
