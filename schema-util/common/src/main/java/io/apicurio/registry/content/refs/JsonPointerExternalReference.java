package io.apicurio.registry.content.refs;

public class JsonPointerExternalReference extends ExternalReference {

    private static String toFullReference(String resource, String component) {
        if (resource == null) {
            return component;
        }
        if (component == null) {
            return resource;
        }
        return resource + component;
    }

    /**
     * Constructor.
     * 
     * @param jsonPointer
     */
    public JsonPointerExternalReference(String jsonPointer) {
        super(jsonPointer, resourceFrom(jsonPointer), componentFrom(jsonPointer));
    }

    public JsonPointerExternalReference(String resource, String component) {
        super(toFullReference(resource, component), resource, component);
    }

    private static String componentFrom(String jsonPointer) {
        int idx = jsonPointer.indexOf('#');
        if (idx == 0) {
            return jsonPointer;
        } else if (idx > 0) {
            return jsonPointer.substring(idx);
        } else {
            return null;
        }
    }

    private static String resourceFrom(String jsonPointer) {
        int idx = jsonPointer.indexOf('#');
        if (idx == 0) {
            return null;
        } else if (idx > 0) {
            return jsonPointer.substring(0, idx);
        } else {
            return jsonPointer;
        }
    }

}
