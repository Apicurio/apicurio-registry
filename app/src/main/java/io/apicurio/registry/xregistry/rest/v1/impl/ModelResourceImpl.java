package io.apicurio.registry.xregistry.rest.v1.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.xregistry.rest.v1.ModelResource;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ModelResourceImpl extends AbstractXRegistryResource implements ModelResource {

    private static final String SCHEMA_MODEL_JSON = "{"
            + "\"schemas\": {"
            + "  \"singular\": \"schema\","
            + "  \"plural\": \"schemas\","
            + "  \"typemap\": {"
            + "    \"*\": {"
            + "      \"*\": {"
            + "        \"format\": \"string\""
            + "      }"
            + "    }"
            + "  },"
            + "  \"hasdocument\": true,"
            + "  \"versions\": 1,"
            + "  \"flags\": [],"
            + "  \"maxversions\": 0,"
            + "  \"attributes\": {}"
            + "}"
            + "}";

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getRegistryModel(String schema, String specversion) {
        return Response.ok(SCHEMA_MODEL_JSON, MediaType.APPLICATION_JSON).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public Response putRegistryModel(String specversion, InputStream data) {
        throw methodNotAllowed();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public Response patchRegistryModel(String specversion, InputStream data) {
        throw methodNotAllowed();
    }
}
