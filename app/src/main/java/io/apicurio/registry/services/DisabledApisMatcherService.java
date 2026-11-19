package io.apicurio.registry.services;

import io.apicurio.common.apps.config.Info;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_API;

@ApplicationScoped
public class DisabledApisMatcherService {

    @Inject
    Logger log;

    private static final String UI_PATTERN = "/ui/.*";

    private final List<Pattern> disabledPatternsList = new ArrayList<>();

    @Inject
    @ConfigProperty(name = "apicurio.disable.apis")
    @Info(category = CATEGORY_API, description = "Disable APIs", availableSince = "2.0.0.Final")
    Optional<List<String>> disableRegexps;

    public void init(@Observes StartupEvent ev) {

        List<String> regexps = new ArrayList<>();

        disableRegexps.ifPresent(regexps::addAll);
        for (String regexp : regexps) {
            try {
                Pattern p = Pattern.compile(regexp);
                disabledPatternsList.add(p);
            } catch (PatternSyntaxException e) {
                log.error("An error occurred parsing a regexp for disabling APIs: " + regexp, e);
            }
        }
    }

    /**
     * Whether the request path is disabled. The patterns are matched against the raw path and against
     * {@link #normalizePath(String) the path that routing resolves}, so that matrix parameters,
     * percent-encoding, empty segments or dot segments cannot make a disabled path look different.
     */
    public boolean isDisabled(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        String normalizedPath = normalizePath(requestPath);
        String normalizedEncodedPath = normalizePath(requestPath, false);
        for (Pattern pattern : disabledPatternsList) {
            if (pattern.matcher(requestPath).matches() || pattern.matcher(normalizedPath).matches()
                    || pattern.matcher(normalizedEncodedPath).matches()) {
                log.warn("Request {} is rejected because it's disabled by pattern {}", requestPath,
                        pattern.pattern());
                return true;
            }
        }
        return false;
    }

    /**
     * The path the way routing sees it: matrix parameters removed from every segment
     * ({@code /apis;x=1/ccompat} is {@code /apis/ccompat}), percent-encoded octets decoded, empty and
     * {@code .} segments dropped, and {@code ..} segments resolved. A trailing slash is kept.
     */
    static String normalizePath(String rawPath) {
        return normalizePath(rawPath, true);
    }

    /**
     * @param decode whether to percent-decode the segments in the result. Routing keeps segment
     *            boundaries as they are encoded ({@code a%2Fb} is one segment), which a pattern such as
     *            {@code [^/]+} only matches in the encoded form; dot segments are recognised either way.
     */
    static String normalizePath(String rawPath, boolean decode) {
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : rawPath.split("/", -1)) {
            int matrix = segment.indexOf(';');
            String encoded = matrix >= 0 ? segment.substring(0, matrix) : segment;
            String name = percentDecode(encoded);
            if (name.isEmpty() || ".".equals(name)) {
                continue;
            }
            if ("..".equals(name)) {
                segments.pollLast();
            } else {
                segments.addLast(decode ? name : encoded);
            }
        }
        String normalized = "/" + String.join("/", segments);
        return rawPath.endsWith("/") && !normalized.endsWith("/") ? normalized + "/" : normalized;
    }

    private static String percentDecode(String value) {
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '%' && i + 2 < bytes.length && Character.digit(bytes[i + 1], 16) >= 0
                    && Character.digit(bytes[i + 2], 16) >= 0) {
                out.write(Character.digit(bytes[i + 1], 16) * 16 + Character.digit(bytes[i + 2], 16));
                i += 2;
            } else {
                out.write(bytes[i]);
            }
        }
        return out.toString(StandardCharsets.UTF_8);
    }

}
