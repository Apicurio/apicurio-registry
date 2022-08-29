package io.apicurio.registry.systemtests.framework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class Base64Utils {
    public static String encode(Path path) {
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encode(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String data) {
        return new String(Base64.getDecoder().decode(data));
    }
}
