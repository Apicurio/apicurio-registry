package io.apicurio.multitenant.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.multitenant.client.exception.TenantManagerClientException;
import io.apicurio.multitenant.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.datamodel.RegistryTenant;

public class TenantManagerClientImpl implements TenantManagerClient {

    private static final String TENANTS_API_BASE_PATH = "api/v1/tenants";

    private HttpClient client;
    private ObjectMapper mapper;

    private String endpoint;

    public TenantManagerClientImpl(String endpoint) {
        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        this.endpoint = endpoint;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public List<RegistryTenant> listTenants() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), new TypeReference<List<RegistryTenant>>(){});
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant createTenant(NewRegistryTenantRequest tenantRequest) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + TENANTS_API_BASE_PATH))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofByteArray(this.mapper.writeValueAsBytes(tenantRequest)))
                    .build();

            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 201) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public RegistryTenant getTenant(String tenantId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() == 200) {
                return this.mapper.readValue(res.body(), RegistryTenant.class);
            }
            throw new TenantManagerClientException(res.toString());
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

    @Override
    public void deleteTenant(String tenantId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + TENANTS_API_BASE_PATH + "/" + tenantId))
                .DELETE()
                .build();
        try {
            HttpResponse<InputStream> res = client.send(req, BodyHandlers.ofInputStream());
            if (res.statusCode() != 204) {
                throw new TenantManagerClientException(res.toString());
            }
        } catch ( IOException | InterruptedException e ) {
            throw new TenantManagerClientException(e);
        }
    }

}
