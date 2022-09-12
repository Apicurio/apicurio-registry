/*
 *
 * (C) Copyright IBM Corp. 2019  All Rights Reserved.
 *
 */
package io.apicurio.registry.ibmcompat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import io.apicurio.registry.utils.JAXRSClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


/* Intended for internal use only */
@JsonDeserialize(using = JsonDeserializer.class)
class SchemaRegistryRestAPIClient {

    private static Logger logger = LoggerFactory.getLogger(SchemaRegistryRestAPIClient.class);

    private final String apiKey;

    private final WebTarget schemasEndpoint;
    private final WebTarget schemaVersionsEndpoint;
    private final WebTarget schemaEndpoint;
    private final WebTarget schemaVersionEndpoint;

    SchemaRegistryRestAPIClient(String url, String apikey, boolean skipSslValidation)
    throws Exception {
        apiKey = apikey;

        Client client = getJAXRSClient(skipSslValidation);

        schemasEndpoint = client.target(url).path("/schemas");
        schemaVersionsEndpoint = client.target(url).path("/schemas/{schemaid}/versions");
        schemaEndpoint = client.target(url).path("/schemas/{schemaid}");
        schemaVersionEndpoint = client.target(url).path("/schemas/{schemaid}/versions/{versionnum}");

        // check that we're able to connect to the REST API
        connectionTest(url);
    }

    private Tuple<?> post(String schemaId, Response response) throws Exception {
        int status = response.getStatus();
        try {
            if (status == 201)
                return new Tuple<>(status, response.readEntity(SchemaInfoResponse.class));
            else if (status == 200)
                return new Tuple<>(status, response.readEntity(String.class));
            else
                handleErrorResponse(response, schemaId, null);
        } finally {
            response.close();
        }
        logger.error("Unexpected response from Schema Registry. Status code {}", status);
        throw new Exception("HTTP response status: " + status);
    }

    Tuple<?> create(String schemaId, String schema, boolean verify) throws Exception {
        Response response = schemasEndpoint.queryParam("verify", verify)
                                           .request(MediaType.APPLICATION_JSON_TYPE)
                                           .post(Entity.entity(new NewSchema(schemaId, schema), MediaType.APPLICATION_JSON_TYPE));
        return post(schemaId, response);
    }

    Tuple<?> update(String schemaId, String schema, boolean verify) throws Exception {
        Response response = schemaVersionsEndpoint.resolveTemplate("schemaid", schemaId)
                                                  .queryParam("verify", verify)
                                                  .request(MediaType.APPLICATION_JSON_TYPE)
                                                  .post(Entity.entity(new NewSchemaVersion(schema), MediaType.APPLICATION_JSON_TYPE));
        return post(schemaId, response);
    }

    SchemaResponse get(String schemaId, String version) throws Exception {
        Response response = getResponse(schemaVersionEndpoint.resolveTemplateFromEncoded("schemaid", schemaId)
                                                             .resolveTemplate("versionnum", version)
                                                             .request(MediaType.APPLICATION_JSON_TYPE)
                                                             .accept(MediaType.APPLICATION_JSON));

        int status = response.getStatus();
        try {
            if (status == 200)
                return response.readEntity(SchemaResponse.class);
            else
                handleErrorResponse(response, schemaId, version);
        } finally {
            response.close();
        }
        logger.error("Unexpected response from Schema Registry. Status code {}", status);
        throw new Exception("HTTP response status: " + status);
    }

    SchemaInfoResponse get(String schemaId) throws Exception {
        Response response = getResponse(schemaEndpoint.resolveTemplateFromEncoded("schemaid", schemaId)
                                                      .request(MediaType.APPLICATION_JSON_TYPE)
                                                      .accept(MediaType.APPLICATION_JSON));
        int status = response.getStatus();
        try {
            if (status == 200) {
                return response.readEntity(SchemaInfoResponse.class);
            } else {
                handleErrorResponse(response, schemaId, null);
            }
        } finally {
            response.close();
        }
        logger.error("Unexpected response from Schema Registry. Status code {}", status);
        throw new Exception("HTTP response status: " + status);

    }

    public void handleErrorResponse(Response response, String schemaId, String version)
    throws Exception {
        int status = response.getStatus();

        if (status == 404) {
            ErrorResponse jsonObject = response.readEntity(ErrorResponse.class);

            if (jsonObject.message != null &&
                (jsonObject.message.equals("Schema not found") || jsonObject.message.equals("Schema version not found"))) {
                if (version == null) {
                    throw new Exception("Schema id: " + schemaId);
                } else {
                    throw new Exception("Schema id: " + schemaId + ", version id: " + version);
                }
            }
        } else if (status == 401 || status == 403) {
            logger.error("Auth failure. Status code {}", status);
            throw new Exception("HTTP response status: " + status);
        } else if (status == 500) {
            ErrorResponse obj = response.readEntity(ErrorResponse.class);
            logger.error("Server error from Schema Registry : {}", obj.message);
            throw new Exception("HTTP response status: " + status + " -- " + obj.message);
        } else if (status > 500) {
            logger.error("Unexpected server error from Schema Registry. Status code {}", status);
            throw new Exception("HTTP response status: " + status);
        }
    }

    /**
     * Confirm that we're able to connect to the Schema Registry. Used to validate
     * the URL and API key that we have been given.
     */
    private void connectionTest(String url) throws Exception {
        // TODO - Fetching all the schemas. A less expensive test would be nice
        Response response = getResponse(schemasEndpoint.request());

        int status = response.getStatus();
        try {
            if (status == 200) {
                logger.debug("Connected to Schema Registry {}", url);
            } else if (status == 401 || status == 403) {
                logger.error("Failed to connect to Schema Registry. Status code {}", status);
                throw new Exception("HTTP response status: " + status);
            } else if (status == 500) {
                ErrorResponse obj = response.readEntity(ErrorResponse.class);
                logger.error("Server error from Schema Registry : {}", obj.message);
                throw new Exception("HTTP response status: " + status + " -- " + obj.message);
            } else if (status > 500) {
                logger.error("Server error from Schema Registry. Status code {}", status);
                throw new Exception("HTTP response status: " + status);
            } else {
                logger.error("Unexpected response. Status code {}", status);
                throw new Exception("HTTP response status: " + status);
            }
        } finally {
            response.close();
        }
    }


    private Response getResponse(Builder builder) throws Exception {
        try {
            return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                          .get();
        } catch (ProcessingException pe) {
            logger.error("Failed to make API request", pe);
            Throwable cause = pe.getCause();
            throw new Exception(cause != null ? cause : pe);
        }
    }

    private static Client getJAXRSClient(boolean skipSSLValidation) throws KeyManagementException, NoSuchAlgorithmException {
        Client newClient = JAXRSClientUtil.getJAXRSClient(skipSSLValidation);

        newClient.register(JacksonJsonProvider.class);

        return newClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NewSchemaVersion {
        @JsonProperty
        String definition;

        public NewSchemaVersion() {
        }

        public NewSchemaVersion(String definition) {
            this.definition = definition;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NewSchema extends NewSchemaVersion {
        @JsonProperty
        String name;

        public NewSchema() {
        }

        public NewSchema(String name, String definition) {
            super(definition);
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ErrorResponse {
        @JsonProperty
        String message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SchemaInfoResponse {
        @JsonProperty
        String id;
        @JsonProperty
        String name;
        @JsonProperty
        StateResponse state;
        @JsonProperty
        boolean enabled;
        @JsonProperty
        List<VersionResponse> versions = new ArrayList<VersionResponse>();
        @JsonProperty
        List<String> topics = new ArrayList<String>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SchemaResponse {
        @JsonProperty
        String id;
        @JsonProperty
        VersionResponse version;
        @JsonProperty
        String name;
        @JsonProperty
        StateResponse state;
        @JsonProperty
        boolean enabled;
        @JsonProperty
        String definition;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class VersionResponse {
        @JsonProperty
        String id;
        @JsonProperty
        String name;
        @JsonProperty
        StateResponse state;
        @JsonProperty
        boolean enabled;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StateResponse {
        @JsonProperty
        String state;
        @JsonProperty
        String comment;
    }

    static class Tuple<T> {
        int status;
        T result;

        Tuple(int status, T result) {
            this.status = status;
            this.result = result;
        }
    }
}
