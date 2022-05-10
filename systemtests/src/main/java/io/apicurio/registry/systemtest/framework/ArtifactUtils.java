package io.apicurio.registry.systemtest.framework;

import org.json.JSONArray;
import org.json.JSONObject;

public class ArtifactUtils {
    public static String getDefaultAvroArtifact() {
        return new JSONObject()
                .put("type", "record")
                .put("name", "price")
                .put("namespace", "com.example")
                .put("fields", new JSONArray() {{
                    put(new JSONObject() {{
                        put("name", "symbol");
                        put("type", "string");
                    }});
                    put(new JSONObject() {{
                        put("name", "price");
                        put("type", "string");
                    }});
                }})
                .toString();
    }
}
