package io.apicurio.registry.utils.protobuf.schema;

import okio.FileHandle;
import okio.FileSystem;
import okio.Path;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtoContent {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*)\\s*;", Pattern.MULTILINE);

    public static String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String extractFilename(String importPath) {
        int lastSlash = importPath.lastIndexOf('/');
        return lastSlash == -1 ? importPath : importPath.substring(lastSlash + 1);
    }

    public static String buildExpectedImportPath(String packageName, String filename) {
        if (packageName == null || packageName.isEmpty()) {
            return filename;
        }
        String packagePath = packageName.replace('.', '/');
        return packagePath + "/" + filename;
    }

    private final String importPath;
    private final String filename;
    private String content;
    private final String packageName;
    private final String expectedImportPath;

    public ProtoContent(String importPath, String content) {
        this.importPath = importPath;
        this.filename = extractFilename(importPath);
        this.content = content;
        this.packageName = extractPackageName(content);
        this.expectedImportPath = buildExpectedImportPath(packageName, filename);
    }

    public String getImportPath() {
        return importPath;
    }

    public String getContent() {
        return content;
    }

    public String getFilename() {
        return filename;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getExpectedImportPath() {
        return expectedImportPath;
    }

    public boolean isImportPathMismatched() {
        return !importPath.equals(expectedImportPath);
    }

    public void fixImport(String importPath, String updatedImportPath) {
        String escapedImportPath = Pattern.quote(importPath);
        String importPattern = "^(\\s*import\\s+(?:public\\s+|weak\\s+)?\")(" + escapedImportPath + ")(\";)";
        Pattern pattern = Pattern.compile(importPattern, Pattern.MULTILINE);
        this.content = pattern.matcher(this.content).replaceAll("$1" + updatedImportPath + "$3");
    }

    public Path writeTo(FileSystem fileSystem) throws IOException {
        FileHandle fileHandle = null;
        try {
            String protoFileName = this.getExpectedImportPath();
            okio.Path path = okio.Path.get(protoFileName);
            
            // Create parent directory if it doesn't exist
            okio.Path parentDir = path.parent();
            if (parentDir != null) {
                fileSystem.createDirectories(parentDir);
            }
            
            final byte[] schemaBytes = getContent().getBytes(StandardCharsets.UTF_8);
            fileHandle = fileSystem.openReadWrite(path);
            fileHandle.write(0, schemaBytes, 0, schemaBytes.length);
            fileHandle.close();
            return path;
        } finally {
            if (fileHandle != null) {
                fileHandle.close();
            }
        }
    }

}