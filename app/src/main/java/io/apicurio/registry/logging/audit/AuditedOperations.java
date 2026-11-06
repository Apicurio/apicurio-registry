package io.apicurio.registry.logging.audit;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which REST operations produce an audit event and derives the parts of the event that come from
 * the endpoint definition. Shared by {@link AuditEventFilter} at runtime and by the coverage test, so the
 * test checks the rule the filter applies.
 */
public final class AuditedOperations {

    /**
     * HTTP verbs whose operations are audited unless the implementation is marked {@link AuditExempt}.
     */
    public static final Set<String> MUTATING_VERBS = Set.of(HttpMethod.POST, HttpMethod.PUT,
            HttpMethod.PATCH, HttpMethod.DELETE);

    private static final Pattern PATH_PARAM = Pattern.compile("\\{\\s*([^}:\\s]+)\\s*(?::[^}]*)?}");

    /**
     * How an implementation method is treated.
     */
    public enum Mode {
        /** Audited when the verb is mutating, otherwise only when denied. */
        DEFAULT,
        /** {@link AuditExempt}: only denied calls are audited. */
        EXEMPT,
        /** {@link AuditAlways}: every call is audited. */
        ALWAYS
    }

    private AuditedOperations() {
    }

    public static Mode modeOf(Method implementation) {
        AuditExempt exempt = implementation.getAnnotation(AuditExempt.class);
        if (exempt != null && !exempt.reason().isBlank()) {
            return Mode.EXEMPT;
        }
        AuditAlways always = implementation.getAnnotation(AuditAlways.class);
        if (always != null && !always.reason().isBlank()) {
            return Mode.ALWAYS;
        }
        return Mode.DEFAULT;
    }

    /**
     * Whether a request to an operation produces an event: every authentication or authorization denial
     * (401, 403) whatever the verb, every call to an {@link Mode#ALWAYS} operation, and every call with a
     * mutating verb to a {@link Mode#DEFAULT} operation.
     */
    public static boolean shouldEmit(String httpMethod, Mode mode, int status) {
        if (isDenied(status)) {
            return true;
        }
        return switch (mode) {
            case ALWAYS -> true;
            case EXEMPT -> false;
            case DEFAULT -> httpMethod != null && MUTATING_VERBS.contains(httpMethod.toUpperCase(Locale.ROOT));
        };
    }

    /**
     * Whether a status means authentication (401) or authorization (403) denied the request.
     */
    public static boolean isDenied(int status) {
        return status == 401 || status == 403;
    }

    /**
     * The full path template of an endpoint: the resource class {@code @Path} followed by the method
     * {@code @Path}, with single slashes.
     */
    public static String pathTemplateOf(Class<?> resourceClass, Method method) {
        Path classPath = resourceClass.getAnnotation(Path.class);
        Path methodPath = method.getAnnotation(Path.class);
        String joined = (classPath == null ? "" : classPath.value()) + "/"
                + (methodPath == null ? "" : methodPath.value());
        return "/" + String.join("/", segments(joined));
    }

    /**
     * The API family of an endpoint path template: {@code v2} and {@code v3} for the core registry API,
     * {@code <name>-<version>} for the other APIs under {@code /apis} (for example {@code ccompat-v7},
     * {@code iceberg-v1}), and the first segment without its leading dot otherwise (for example
     * {@code well-known}).
     */
    public static String apiOf(String pathTemplate) {
        List<String> segments = segments(pathTemplate);
        if (segments.isEmpty()) {
            return "";
        }
        if ("apis".equals(segments.get(0)) && segments.size() >= 3) {
            if ("registry".equals(segments.get(1))) {
                return segments.get(2);
            }
            return segments.get(1) + "-" + segments.get(2);
        }
        String first = segments.get(0);
        return first.startsWith(".") ? first.substring(1) : first;
    }

    /**
     * The last fixed (non-parameter) segment of a path template, used as the event's resource kind.
     */
    public static String resourceOf(String pathTemplate) {
        List<String> segments = segments(pathTemplate);
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (!segments.get(i).startsWith("{")) {
                return segments.get(i);
            }
        }
        return null;
    }

    /**
     * The path parameter names of a template, in the order they appear.
     */
    public static List<String> pathParamsOf(String pathTemplate) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PATH_PARAM.matcher(pathTemplate);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }
}
