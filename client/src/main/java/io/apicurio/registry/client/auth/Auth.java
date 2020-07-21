package io.apicurio.registry.client.auth;


public class Auth {
   private AuthConfig config;

   public Auth(AuthConfig config) {
      this.config = config;
   }

   public AuthStrategy getAuthStrategy() {
      switch(this.config.getProvider()) {
         case KEYCLOAK: return new KeycloakAuth(config);
      }
      return null;
   }

}
