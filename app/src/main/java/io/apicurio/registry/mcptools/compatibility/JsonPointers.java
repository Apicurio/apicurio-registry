package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.core.JsonPointer;

import java.util.ArrayList;
import java.util.List;

final class JsonPointers {

    private JsonPointers() {
    }

    /**
     * Appends property names to a JSON Pointer, escaping {@code ~} and {@code /} in each one.
     */
    static String append(String pointer, String... properties) {
        JsonPointer result = JsonPointer.compile(pointer);
        for (String property : properties) {
            result = result.appendProperty(property);
        }
        return result.toString();
    }

    /**
     * Whether {@code pointer} addresses {@code node} itself or something inside it. Pointers are
     * compared by decoded reference tokens, so {@code /properties/a} does not contain
     * {@code /properties/ab}.
     */
    static boolean isAtOrBelow(String pointer, String node) {
        List<String> pointerTokens = tokens(pointer);
        List<String> nodeTokens = tokens(node);
        return pointerTokens.size() >= nodeTokens.size()
                && pointerTokens.subList(0, nodeTokens.size()).equals(nodeTokens);
    }

    private static List<String> tokens(String pointer) {
        List<String> tokens = new ArrayList<>();
        for (JsonPointer current = JsonPointer.compile(pointer); !current.matches();
                current = current.tail()) {
            tokens.add(current.getMatchingProperty());
        }
        return tokens;
    }
}
