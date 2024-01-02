package io.apicurio.registry.content.refs;

public class JsonPointerExternalReference extends ExternalReference {

    /**
     * Constructor.
     * @param jsonPointer
     */
    public JsonPointerExternalReference(String jsonPointer) {
        super(jsonPointer, resourceFrom(jsonPointer), componentFrom(jsonPointer));
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
