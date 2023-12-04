package io.apicurio.tests.utils;

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.restassured.response.Response;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


public class ArtifactUtils {


    public static Response getArtifact(String groupId, String artifactId) {
        return getArtifact(groupId, artifactId, "", 200);
    }

    public static Response getArtifact(String groupId, String artifactId, int returnCode) {
        return getArtifact(groupId, artifactId, "", returnCode);
    }

    public static Response getArtifact(String groupId, String artifactId, String version, int returnCode) {
        return
            BaseHttpUtils.getRequest(RestConstants.JSON, ApicurioRegistryBaseIT.getRegistryV2ApiUrl() + "/groups/" + encodeURIComponent(groupId) + "/artifacts/" + encodeURIComponent(artifactId) + "/" + version, returnCode);
    }

    public static Response createArtifact(String groupId, String artifactId, String artifact, int returnCode) {
        return  BaseHttpUtils.artifactPostRequest(artifactId, RestConstants.JSON, artifact, ApicurioRegistryBaseIT.getRegistryV2ApiUrl() + "/groups/" + encodeURIComponent(groupId) + "/artifacts", returnCode);
    }

    // ================================================================================

    private static String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

}
