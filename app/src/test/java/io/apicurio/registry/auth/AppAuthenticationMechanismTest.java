package io.apicurio.registry.auth;

import io.quarkus.vertx.http.runtime.security.BasicAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class AppAuthenticationMechanismTest {

    private AppAuthenticationMechanism mechanism;
    private AuthConfig authConfig;

    @BeforeEach
    public void setup() {
        mechanism = new AppAuthenticationMechanism();
        
        authConfig = new AuthConfig();
        mechanism.authConfig = authConfig;
        
        mechanism.log = Mockito.mock(Logger.class);
        mechanism.basicAuthenticationMechanism = Mockito.mock(BasicAuthenticationMechanism.class);
        mechanism.formAuthenticationMechanism = Mockito.mock(Instance.class);
        when(mechanism.formAuthenticationMechanism.isResolvable()).thenReturn(true);
        when(mechanism.formAuthenticationMechanism.get()).thenReturn(Mockito.mock(FormAuthenticationMechanism.class));
        mechanism.oidcAuthenticationMechanism = Mockito.mock(OidcAuthenticationMechanism.class);
        mechanism.proxyHeaderAuthenticationMechanism = Mockito.mock(ProxyHeaderAuthenticationMechanism.class);
        mechanism.kubernetesClient = Mockito.mock(Instance.class);
        when(mechanism.kubernetesClient.isResolvable()).thenReturn(false);
    }

    @Test
    public void testBuildAuthChain_DefaultPriority() {
        // Setup config
        authConfig.basicAuthEnabled = true;
        authConfig.formAuthEnabled = true;
        authConfig.proxyHeaderAuthEnabled = true;
        authConfig.oidcAuthEnabled = true;
        authConfig.mechanismPriority = "basic,form,proxy-header,oidc";
        
        List<AuthenticationStrategy> chain = mechanism.buildAuthChain();
        
        assertEquals(4, chain.size());
        assertEquals("basic", chain.get(0).name());
        assertEquals("form", chain.get(1).name());
        assertEquals("proxy-header", chain.get(2).name());
        assertEquals("oidc", chain.get(3).name());
    }

    @Test
    public void testBuildAuthChain_ReorderedPriority() {
        // Setup config
        authConfig.basicAuthEnabled = true;
        authConfig.formAuthEnabled = true;
        authConfig.proxyHeaderAuthEnabled = true;
        authConfig.oidcAuthEnabled = true;
        authConfig.mechanismPriority = "oidc,proxy-header,form,basic";
        
        List<AuthenticationStrategy> chain = mechanism.buildAuthChain();
        
        assertEquals(4, chain.size());
        assertEquals("oidc", chain.get(0).name());
        assertEquals("proxy-header", chain.get(1).name());
        assertEquals("form", chain.get(2).name());
        assertEquals("basic", chain.get(3).name());
    }

    @Test
    public void testBuildAuthChain_DisabledMechanisms() {
        // Setup config
        authConfig.basicAuthEnabled = false;
        authConfig.formAuthEnabled = true;
        authConfig.proxyHeaderAuthEnabled = false;
        authConfig.oidcAuthEnabled = true;
        authConfig.mechanismPriority = "basic,form,proxy-header,oidc";
        
        List<AuthenticationStrategy> chain = mechanism.buildAuthChain();
        
        // basic and proxy-header are disabled, so they shouldn't be in the chain
        assertEquals(2, chain.size());
        assertEquals("form", chain.get(0).name());
        assertEquals("oidc", chain.get(1).name());
    }
}
