package io.apicurio.registry.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.restassured.RestAssured;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.*;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.*;

public class KeycloakResourceManager implements QuarkusTestResourceLifecycleManager {
	private static final String KEYCLOAK_SERVER_URL = System.getProperty("keycloak.url", "http://localhost:8090/auth");
	private static final String KEYCLOAK_REALM = "registry";
	public static final String REGISTRY_APP = "registry-api";

	static {
		RestAssured.useRelaxedHTTPSValidation();
	}

	@Override
	public Map<String, String> start() {
		RealmRepresentation realm = createRealm(KEYCLOAK_REALM);

		realm.getClients().add(createClient(REGISTRY_APP));
		realm.getUsers().add(createUser("alice", "user"));
		realm.getUsers().add(createUser("admin", "user", "admin"));
		try {
			RestAssured
					.given()
					.auth().oauth2(getAdminToken())
					.contentType("application/json")
					.body(JsonSerialization.writeValueAsBytes(realm))
					.when()
					.post(KEYCLOAK_SERVER_URL + "/admin/realms").then()
					.statusCode(201);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return Collections.emptyMap();
	}

	private static String getAdminToken() {
		return RestAssured
				.given()
				.param("grant_type", "password")
				.param("username", "admin")
				.param("password", "admin")
				.param("client_id", "admin-cli")
				.when()
				.post(KEYCLOAK_SERVER_URL + "/realms/master/protocol/openid-connect/token")
				.as(AccessTokenResponse.class).getToken();
	}

	private static RealmRepresentation createRealm(String name) {
		RealmRepresentation realm = new RealmRepresentation();

		realm.setRealm(name);
		realm.setEnabled(true);
		realm.setUsers(new ArrayList<>());
		realm.setClients(new ArrayList<>());
		realm.setAccessTokenLifespan(3);

		RolesRepresentation roles = new RolesRepresentation();
		List<RoleRepresentation> realmRoles = new ArrayList<>();

		roles.setRealm(realmRoles);
		realm.setRoles(roles);

		realm.getRoles().getRealm().add(new RoleRepresentation("user", null, false));
		realm.getRoles().getRealm().add(new RoleRepresentation("admin", null, false));

		return realm;
	}

	private static ClientRepresentation createClient(String clientId) {
		ClientRepresentation client = new ClientRepresentation();
		client.setClientId(clientId);
		client.setPublicClient(true);
		client.setDirectAccessGrantsEnabled(true);
		client.setEnabled(true);
		return client;
	}

	private static UserRepresentation createUser(String username, String... realmRoles) {
		UserRepresentation user = new UserRepresentation();

		user.setUsername(username);
		user.setEnabled(true);
		user.setCredentials(new ArrayList<>());
		user.setRealmRoles(Arrays.asList(realmRoles));
		user.setEmail(username + "@redhat.com");

		CredentialRepresentation credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(username);
		credential.setTemporary(false);

		user.getCredentials().add(credential);

		return user;
	}

	@Override
	public void stop() {
		RestAssured
				.given()
				.auth().oauth2(getAdminToken())
				.when()
				.delete(KEYCLOAK_SERVER_URL + "/admin/realms/" + KEYCLOAK_REALM).then().statusCode(204);

	}
}
