package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.BaseHttpUtils;
import io.apicurio.tests.utils.RestConstants;
import io.restassured.response.Response;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

import static io.apicurio.tests.utils.BaseHttpUtils.putRequest;

public class ConfluentSubjectsUtils {

    public static Response getAllSchemas(int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "", getCCompatURL("/ccompat/v7/subjects"),
                returnCode);
    }

    public static Response getLatestVersionSchema(String nameOfSchema) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, "",
                getCCompatURL("/ccompat/v7/subjects/" + nameOfSchema + "/versions/latest"), 200);
    }

    public static Response createSchema(String schemeDefinition, String schemaName, int returnCode) {
        return BaseHttpUtils.postRequest(RestConstants.JSON, schemeDefinition,
                getCCompatURL("/ccompat/v7/subjects/" + schemaName + "/versions"), returnCode);
    }

    public static Response updateSchemaMetadata(String schemaName, String metadata, int returnCode) {
        return putRequest(RestConstants.JSON, metadata,
                getCCompatURL("/ccompat/v7/subjects/" + schemaName + "/meta"), returnCode);
    }

    private static URL getCCompatURL(String ccompatPath) {
        try {
            return new URL(ApicurioRegistryBaseIT.getRegistryApiUrl() + ccompatPath);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

}
