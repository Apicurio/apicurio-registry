package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.rest.v3.impl.UsersResourceImpl;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
public class UsersResourceTest extends AbstractResourceTestBase {

    @Test
    public void testGetDisplayNameWithBothNames() {
        UsersResourceImpl usersResource = new UsersResourceImpl();
        usersResource.authConfig = mock(AuthConfig.class);
        usersResource.securityIdentity = mock(SecurityIdentity.class);
        usersResource.rbac = mock(RoleBasedAccessController.class);
        
        Instance<JsonWebToken> jsonWebTokenInstance = mock(Instance.class);
        JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        
        when(jsonWebTokenInstance.isResolvable()).thenReturn(true);
        when(jsonWebTokenInstance.get()).thenReturn(jsonWebToken);
        when(jsonWebToken.getClaim("first_name")).thenReturn("John");
        when(jsonWebToken.getClaim("last_name")).thenReturn("Doe");
        when(usersResource.securityIdentity.getPrincipal()).thenReturn(() -> "johndoe");
        
        usersResource.jsonWebToken = jsonWebTokenInstance;
        
        String displayName = usersResource.getDisplayName();
        assertEquals("John Doe", displayName);
    }

    @Test
    public void testGetDisplayNameWithFirstNameOnly() {
        UsersResourceImpl usersResource = new UsersResourceImpl();
        usersResource.securityIdentity = mock(SecurityIdentity.class);
        
        Instance<JsonWebToken> jsonWebTokenInstance = mock(Instance.class);
        JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        
        when(jsonWebTokenInstance.isResolvable()).thenReturn(true);
        when(jsonWebTokenInstance.get()).thenReturn(jsonWebToken);
        when(jsonWebToken.getClaim("first_name")).thenReturn("John");
        when(jsonWebToken.getClaim("last_name")).thenReturn(null);
        when(usersResource.securityIdentity.getPrincipal()).thenReturn(() -> "johndoe");
        
        usersResource.jsonWebToken = jsonWebTokenInstance;
        
        String displayName = usersResource.getDisplayName();
        assertEquals("John", displayName);
    }

    @Test
    public void testGetDisplayNameWithLastNameOnly() {
        UsersResourceImpl usersResource = new UsersResourceImpl();
        usersResource.securityIdentity = mock(SecurityIdentity.class);
        
        Instance<JsonWebToken> jsonWebTokenInstance = mock(Instance.class);
        JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        
        when(jsonWebTokenInstance.isResolvable()).thenReturn(true);
        when(jsonWebTokenInstance.get()).thenReturn(jsonWebToken);
        when(jsonWebToken.getClaim("first_name")).thenReturn(null);
        when(jsonWebToken.getClaim("last_name")).thenReturn("Doe");
        when(usersResource.securityIdentity.getPrincipal()).thenReturn(() -> "johndoe");
        
        usersResource.jsonWebToken = jsonWebTokenInstance;
        
        String displayName = usersResource.getDisplayName();
        assertEquals("Doe", displayName);
    }

    @Test
    public void testGetDisplayNameWithNoClaims() {
        UsersResourceImpl usersResource = new UsersResourceImpl();
        usersResource.securityIdentity = mock(SecurityIdentity.class);
        
        Instance<JsonWebToken> jsonWebTokenInstance = mock(Instance.class);
        JsonWebToken jsonWebToken = mock(JsonWebToken.class);
        
        when(jsonWebTokenInstance.isResolvable()).thenReturn(true);
        when(jsonWebTokenInstance.get()).thenReturn(jsonWebToken);
        when(jsonWebToken.getClaim("first_name")).thenReturn(null);
        when(jsonWebToken.getClaim("last_name")).thenReturn(null);
        when(usersResource.securityIdentity.getPrincipal()).thenReturn(() -> "johndoe");
        
        usersResource.jsonWebToken = jsonWebTokenInstance;
        
        String displayName = usersResource.getDisplayName();
        assertEquals("johndoe", displayName);
    }

    @Test
    public void testGetDisplayNameWithNoToken() {
        UsersResourceImpl usersResource = new UsersResourceImpl();
        usersResource.securityIdentity = mock(SecurityIdentity.class);
        
        Instance<JsonWebToken> jsonWebTokenInstance = mock(Instance.class);
        
        when(jsonWebTokenInstance.isResolvable()).thenReturn(false);
        when(usersResource.securityIdentity.getPrincipal()).thenReturn(() -> "johndoe");
        
        usersResource.jsonWebToken = jsonWebTokenInstance;
        
        String displayName = usersResource.getDisplayName();
        assertEquals("johndoe", displayName);
    }
}
