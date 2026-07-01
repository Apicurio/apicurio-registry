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
    private static final Pattern CONST_PATTERN = Pattern.compile("^\\s*const\\s+([^=]+)=");
    private static final Pattern TYPEDEF_PATTERN = Pattern.compile("^\\s*typedef\\s+(\\S.*)$");
    private static final Pattern ENUM_PATTERN = Pattern.compile("^\\s*enum\\s+(\\w+)\\s*\\{");
    private static final Pattern SENUM_PATTERN = Pattern.compile("^\\s*senum\\s+(\\w+)\\s*\\{");
    private static final Pattern STRUCT_PATTERN = Pattern.compile("^\\s*struct\\s+(\\w+)\\s*\\{");
    private static final Pattern UNION_PATTERN = Pattern.compile("^\\s*union\\s+(\\w+)\\s*\\{");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile("^\\s*exception\\s+(\\w+)\\s*\\{");
    private static final Pattern SERVICE_PATTERN = Pattern
            .compile("^\\s*service\\s+(\\w+)(?:\\s+extends\\s+[\\w.]+)?\\s*\\{");
    private static final Pattern FIELD_PATTERN = Pattern
            .compile("^\\s*\\d+\\s*:\\s*(?:(?:optional|required)\\s+)?\\S+\\s+\\w+");

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

            if (line.isEmpty() || line.matches("[};,]+")) {
                i++;
            } else {
                ParseResult result = parseLine(line, lines, i, document);
                if (result.foundConstruct) {
                    foundThriftConstruct = true;
                }
                i = result.nextIndex;
            }
        }

        if (!foundThriftConstruct) {
            throw new ThriftIdlParseException(
                    "No valid Thrift IDL constructs found (expected namespace, struct, enum, service, etc.)");
        }

        return document;
    }

    private static ParseResult parseLine(String line, List<String> lines, int index,
            ThriftDocument document) {
        Matcher m;

        m = NAMESPACE_PATTERN.matcher(line);
        if (m.find()) {
            document.addNamespace(m.group(1), m.group(2));
            return new ParseResult(index + 1, true);
        }

        m = INCLUDE_PATTERN.matcher(line);
        if (m.find()) {
            document.addInclude(m.group(1));
            return new ParseResult(index + 1, true);
        }

        m = CPP_INCLUDE_PATTERN.matcher(line);
        if (m.find()) {
            return new ParseResult(index + 1, true);
        }

        m = CONST_PATTERN.matcher(line);
        if (m.find()) {
            String[] typeAndName = splitTypeAndName(m.group(1));
            if (typeAndName != null) {
                document.addConstant(typeAndName[1], typeAndName[0]);
                return new ParseResult(index + 1, true);
            }
        }

        m = TYPEDEF_PATTERN.matcher(line);
        if (m.find()) {
            String[] typeAndName = splitTypeAndName(m.group(1));
            if (typeAndName != null) {
                document.addTypedef(typeAndName[1], typeAndName[0]);
                return new ParseResult(index + 1, true);
            }
        }

        ParseResult blockResult = parseBlockConstruct(line, lines, index, document);
        if (blockResult != null) {
            return blockResult;
        }

        if (FIELD_PATTERN.matcher(line).find()) {
            return new ParseResult(index + 1, true);
        }

        return new ParseResult(index + 1, false);
    }

    private static String[] splitTypeAndName(String declaration) {
        String trimmed = declaration.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        int lastTab = trimmed.lastIndexOf('\t');
        int split = Math.max(lastSpace, lastTab);
        if (split < 0) {
            return null;
        }
        String type = trimmed.substring(0, split).trim();
        String name = trimmed.substring(split + 1).trim();
        if (type.isEmpty() || name.isEmpty()) {
            return null;
        }
        return new String[] { type, name };
    }

    private static ParseResult parseBlockConstruct(String line, List<String> lines, int index,
            ThriftDocument document) {
        Matcher m;

        m = ENUM_PATTERN.matcher(line);
        if (m.find()) {
            document.addEnum(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        m = SENUM_PATTERN.matcher(line);
        if (m.find()) {
            document.addEnum(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        m = STRUCT_PATTERN.matcher(line);
        if (m.find()) {
            document.addStruct(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        m = UNION_PATTERN.matcher(line);
        if (m.find()) {
            document.addUnion(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        m = EXCEPTION_PATTERN.matcher(line);
        if (m.find()) {
            document.addException(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        m = SERVICE_PATTERN.matcher(line);
        if (m.find()) {
            document.addService(m.group(1));
            return new ParseResult(skipBlock(lines, index), true);
        }

        return null;
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
        int i = 0;

        while (i < content.length()) {
            char c = content.charAt(i);

            if (c == '"' || c == '\'') {
                i = appendString(content, i, result);
            } else if (c == '#'
                    || (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/')) {
                i = skipLineComment(content, i);
                result.append('\n');
            } else if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '*') {
                i = skipBlockComment(content, i + 2);
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    private static int appendString(String content, int start, StringBuilder result) {
        char quote = content.charAt(start);
        result.append(quote);
        int i = start + 1;

        while (i < content.length()) {
            char c = content.charAt(i);
            result.append(c);
            if (c == '\\' && i + 1 < content.length()) {
                i++;
                result.append(content.charAt(i));
            } else if (c == quote) {
                i++;
                return i;
            }
            i++;
        }

        return i;
    }

    private static int skipLineComment(String content, int start) {
        int i = start;
        while (i < content.length() && content.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private static int skipBlockComment(String content, int start) {
        int i = start;
        while (i < content.length()) {
            if (content.charAt(i) == '*' && i + 1 < content.length()
                    && content.charAt(i + 1) == '/') {
                return i + 2;
            }
            i++;
        }
        return i;
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

    private static class ParseResult {

        final int nextIndex;
        final boolean foundConstruct;

        ParseResult(int nextIndex, boolean foundConstruct) {
            this.nextIndex = nextIndex;
            this.foundConstruct = foundConstruct;
        }
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
