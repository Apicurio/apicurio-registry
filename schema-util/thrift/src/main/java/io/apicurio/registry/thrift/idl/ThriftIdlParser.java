package io.apicurio.registry.thrift.idl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThriftIdlParser {

    private static final Pattern NAMESPACE_PATTERN = Pattern
            .compile("^\\s*namespace\\s+(\\w+)\\s+([\\w.]+)");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*include\\s+\"([^\"]+)\"");
    private static final Pattern CPP_INCLUDE_PATTERN = Pattern.compile("^\\s*cpp_include\\s+\"([^\"]+)\"");
    private static final Pattern CONST_PATTERN = Pattern
            .compile("^\\s*const\\s+(\\S+)\\s+(\\w+)\\s*=");
    private static final Pattern TYPEDEF_PATTERN = Pattern
            .compile("^\\s*typedef\\s+(\\S+)\\s+(\\w+)");
    private static final Pattern ENUM_PATTERN = Pattern.compile("^\\s*enum\\s+(\\w+)\\s*\\{");
    private static final Pattern STRUCT_PATTERN = Pattern.compile("^\\s*struct\\s+(\\w+)\\s*\\{");
    private static final Pattern UNION_PATTERN = Pattern.compile("^\\s*union\\s+(\\w+)\\s*\\{");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("^\\s*exception\\s+(\\w+)\\s*\\{");
    private static final Pattern SERVICE_PATTERN = Pattern
            .compile("^\\s*service\\s+(\\w+)(?:\\s+extends\\s+[\\w.]+)?\\s*\\{");
    private static final Pattern FIELD_PATTERN = Pattern
            .compile("^\\s*\\d+\\s*:\\s*(optional|required)?\\s*\\S+\\s+\\w+");

    private ThriftIdlParser() {
    }

    public static ThriftDocument parse(String content) throws ThriftIdlParseException {
        if (content == null || content.trim().isEmpty()) {
            throw new ThriftIdlParseException("Empty content is not valid Thrift IDL");
        }

        String stripped = stripComments(content);
        List<String> lines = List.of(stripped.split("\\r?\\n"));

        ThriftDocument document = new ThriftDocument();
        boolean foundThriftConstruct = false;

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).trim();

            if (line.isEmpty()) {
                i++;
                continue;
            }

            Matcher m;

            m = NAMESPACE_PATTERN.matcher(line);
            if (m.find()) {
                document.addNamespace(m.group(1), m.group(2));
                foundThriftConstruct = true;
                i++;
                continue;
            }

            m = INCLUDE_PATTERN.matcher(line);
            if (m.find()) {
                document.addInclude(m.group(1));
                foundThriftConstruct = true;
                i++;
                continue;
            }

            m = CPP_INCLUDE_PATTERN.matcher(line);
            if (m.find()) {
                foundThriftConstruct = true;
                i++;
                continue;
            }

            m = CONST_PATTERN.matcher(line);
            if (m.find()) {
                document.addConstant(m.group(2), m.group(1));
                foundThriftConstruct = true;
                i++;
                continue;
            }

            m = TYPEDEF_PATTERN.matcher(line);
            if (m.find()) {
                document.addTypedef(m.group(2), m.group(1));
                foundThriftConstruct = true;
                i++;
                continue;
            }

            m = ENUM_PATTERN.matcher(line);
            if (m.find()) {
                document.addEnum(m.group(1));
                foundThriftConstruct = true;
                i = skipBlock(lines, i);
                continue;
            }

            m = STRUCT_PATTERN.matcher(line);
            if (m.find()) {
                document.addStruct(m.group(1));
                foundThriftConstruct = true;
                i = skipBlock(lines, i);
                continue;
            }

            m = UNION_PATTERN.matcher(line);
            if (m.find()) {
                document.addUnion(m.group(1));
                foundThriftConstruct = true;
                i = skipBlock(lines, i);
                continue;
            }

            m = EXCEPTION_PATTERN.matcher(line);
            if (m.find()) {
                document.addException(m.group(1));
                foundThriftConstruct = true;
                i = skipBlock(lines, i);
                continue;
            }

            m = SERVICE_PATTERN.matcher(line);
            if (m.find()) {
                document.addService(m.group(1));
                foundThriftConstruct = true;
                i = skipBlock(lines, i);
                continue;
            }

            if (FIELD_PATTERN.matcher(line).find()) {
                foundThriftConstruct = true;
                i++;
                continue;
            }

            if (line.matches("[};,]+")) {
                i++;
                continue;
            }

            i++;
        }

        if (!foundThriftConstruct) {
            throw new ThriftIdlParseException(
                    "No valid Thrift IDL constructs found (expected namespace, struct, enum, service, etc.)");
        }

        return document;
    }

    public static boolean isThriftIdl(String content) {
        try {
            parse(content);
            return true;
        } catch (ThriftIdlParseException e) {
            return false;
        }
    }

    public static String stripComments(String content) {
        StringBuilder result = new StringBuilder();
        boolean inBlockComment = false;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inBlockComment) {
                if (c == '*' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }

            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < content.length()) {
                    result.append(content.charAt(i + 1));
                    i++;
                } else if (c == stringChar) {
                    inString = false;
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                result.append(c);
                continue;
            }

            if (c == '/' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if (next == '/') {
                    while (i < content.length() && content.charAt(i) != '\n') {
                        i++;
                    }
                    result.append('\n');
                    continue;
                } else if (next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }

            if (c == '#') {
                while (i < content.length() && content.charAt(i) != '\n') {
                    i++;
                }
                result.append('\n');
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    private static int skipBlock(List<String> lines, int startLine) {
        int braceCount = 0;
        boolean foundOpen = false;
        int i = startLine;

        while (i < lines.size()) {
            String line = lines.get(i);
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    foundOpen = true;
                } else if (c == '}') {
                    braceCount--;
                    if (foundOpen && braceCount == 0) {
                        return i + 1;
                    }
                }
            }
            i++;
        }

        return i;
    }

    public static class ThriftDocument {

        private final List<Namespace> namespaces = new ArrayList<>();
        private final List<String> includes = new ArrayList<>();
        private final List<NamedDefinition> constants = new ArrayList<>();
        private final List<NamedDefinition> typedefs = new ArrayList<>();
        private final List<String> enums = new ArrayList<>();
        private final List<String> structs = new ArrayList<>();
        private final List<String> unions = new ArrayList<>();
        private final List<String> exceptions = new ArrayList<>();
        private final List<String> services = new ArrayList<>();

        void addNamespace(String language, String name) {
            namespaces.add(new Namespace(language, name));
        }

        void addInclude(String path) {
            includes.add(path);
        }

        void addConstant(String name, String type) {
            constants.add(new NamedDefinition(name, type));
        }

        void addTypedef(String name, String type) {
            typedefs.add(new NamedDefinition(name, type));
        }

        void addEnum(String name) {
            enums.add(name);
        }

        void addStruct(String name) {
            structs.add(name);
        }

        void addUnion(String name) {
            unions.add(name);
        }

        void addException(String name) {
            exceptions.add(name);
        }

        void addService(String name) {
            services.add(name);
        }

        public List<Namespace> getNamespaces() {
            return namespaces;
        }

        public List<String> getIncludes() {
            return includes;
        }

        public List<NamedDefinition> getConstants() {
            return constants;
        }

        public List<NamedDefinition> getTypedefs() {
            return typedefs;
        }

        public List<String> getEnums() {
            return enums;
        }

        public List<String> getStructs() {
            return structs;
        }

        public List<String> getUnions() {
            return unions;
        }

        public List<String> getExceptions() {
            return exceptions;
        }

        public List<String> getServices() {
            return services;
        }
    }

    public static class Namespace {

        private final String language;
        private final String name;

        public Namespace(String language, String name) {
            this.language = language;
            this.name = name;
        }

        public String getLanguage() {
            return language;
        }

        public String getName() {
            return name;
        }
    }

    public static class NamedDefinition {

        private final String name;
        private final String type;

        public NamedDefinition(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }
}
