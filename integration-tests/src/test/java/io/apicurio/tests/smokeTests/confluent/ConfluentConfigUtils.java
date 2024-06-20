package io.apicurio.tests.smokeTests.confluent;

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.BaseHttpUtils;
import io.apicurio.tests.utils.RestConstants;
import io.restassured.response.Response;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;

public class ConfluentConfigUtils {

    public static Response testCompatibility(String body, String schemaName, int returnCode) {
        try {
            URL url = new URL(ApicurioRegistryBaseIT.getRegistryApiUrl()
                    + "/ccompat/v7/compatibility/subjects/" + schemaName + "/versions/latest");
            return BaseHttpUtils.rulesPostRequest(RestConstants.SR, body, url, returnCode);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Response createGlobalCompatibilityConfig(String typeOfCompatibility) {
        try {
            URL url = new URL(ApicurioRegistryBaseIT.getRegistryApiUrl() + "/ccompat/v7/config");
            return BaseHttpUtils.putRequest(RestConstants.SR,
                    "{\"compatibility\":\"" + typeOfCompatibility + "\"}", url, 200);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Response getGlobalCompatibilityConfig() {
        try {
            URL url = new URL(ApicurioRegistryBaseIT.getRegistryApiUrl() + "/ccompat/v7/config");
            return BaseHttpUtils.getRequest(RestConstants.JSON, url, 204);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

}
