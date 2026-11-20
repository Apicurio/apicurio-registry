package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import io.apicurio.registry.http.OutboundAddressPolicy;
import io.apicurio.registry.http.OutboundAddressPolicy.AddressVerdict;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether the registry may connect to an MCP server URL supplied in an import request.
 * <p>
 * The URL must use HTTPS (or HTTP when {@code apicurio.mcp-import.insecure-http.enabled}), carry
 * no user information, and name a host allowed by {@code apicurio.mcp-import.allowed-hosts}; an
 * empty list allows nothing. The host is then resolved, and every address it resolves to must be
 * allowed by {@link OutboundAddressPolicy}, whatever the allow-list says.
 * <p>
 * Every refusal has the same message, so the endpoint cannot be used to learn which hosts exist,
 * resolve, or are listed.
 * <p>
 * Known limitation: the HTTP client resolves the host again when it connects, so a host whose
 * DNS answer changes between this check and the connection (DNS rebinding) is not caught here.
 * Allow-listing hostnames under the operator's control, or CIDR ranges, limits this.
 */
@ApplicationScoped
public class McpImportTargetPolicy {

    private static final Logger log = LoggerFactory.getLogger(McpImportTargetPolicy.class);

    static final String DENIED = "The MCP server URL is not allowed by this registry.";

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    @Inject
    McpImportConfig config;

    /**
     * @return the URL to connect to
     * @throws McpImportException with {@link Reason#HOST_DENIED} if the registry may not connect to it
     */
    public URI check(String url) {
        return check(url, config.getAllowedHosts(), config.isInsecureHttpEnabled(), config.isLoopbackEnabled());
    }

    static URI check(String url, List<String> allowedHosts, boolean insecureHttpEnabled, boolean loopbackEnabled) {
        URI uri = parse(url, insecureHttpEnabled);
        String host = uri.getHost();
        String name = normalize(host);
        requireAllowedForm(host, loopbackEnabled);

        boolean hostListed = allowedHosts.stream().anyMatch(entry -> matchesHostEntry(name, entry));
        List<Cidr> ranges = allowedHosts.stream().filter(McpImportTargetPolicy::isAddressEntry)
                .map(Cidr::parse).toList();
        if (!hostListed && ranges.isEmpty()) {
            throw denied("host not listed");
        }
        for (InetAddress address : resolve(name)) {
            requireAllowedAddress(address, loopbackEnabled, hostListed ? List.of() : ranges);
        }
        return uri;
    }

    /** The URL, if it is an absolute http(s) URL with a host and no user information. */
    private static URI parse(String url, boolean insecureHttpEnabled) {
        if (url == null || url.isBlank()) {
            throw invalid("The MCP server URL is required.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw invalid("The MCP server URL is not a valid URI.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && insecureHttpEnabled)) {
            throw invalid("The MCP server URL must use https.");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("The MCP server URL must not contain user information.");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw invalid("The MCP server URL must be an absolute URI with a host.");
        }
        return uri;
    }

    /** Refuses a host written as an address that is never allowed, before any lookup. */
    private static void requireAllowedForm(String host, boolean loopbackEnabled) {
        AddressVerdict verdict;
        try {
            verdict = OutboundAddressPolicy.classifyHost(host, loopbackEnabled);
        } catch (OutboundAddressPolicy.InvalidHostException e) {
            throw denied("ambiguous host form");
        }
        if (verdict != AddressVerdict.ALLOWED) {
            throw denied(verdict.getDescription());
        }
    }

    private static InetAddress[] resolve(String name) {
        try {
            return InetAddress.getAllByName(name);
        } catch (UnknownHostException | SecurityException e) {
            throw denied("host does not resolve");
        }
    }

    /**
     * @param ranges the listed ranges the address must be in; empty when the host itself is listed
     */
    private static void requireAllowedAddress(InetAddress address, boolean loopbackEnabled, List<Cidr> ranges) {
        AddressVerdict resolved = OutboundAddressPolicy.classify(address, loopbackEnabled);
        if (resolved != AddressVerdict.ALLOWED) {
            throw denied("resolves to " + resolved.getDescription());
        }
        if (!ranges.isEmpty() && ranges.stream().noneMatch(range -> range.contains(address))) {
            throw denied("resolves outside the listed ranges");
        }
    }

    private static McpImportException invalid(String message) {
        return new McpImportException(Reason.HOST_DENIED, message);
    }

    private static McpImportException denied(String why) {
        // The reason is for operators only; the client gets the same message for every refusal.
        log.debug("Refused an MCP import target: {}", why);
        return new McpImportException(Reason.HOST_DENIED, DENIED);
    }

    private static String normalize(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) {
            return name.substring(1, name.length() - 1);
        }
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    static boolean isAddressEntry(String entry) {
        String e = entry.trim();
        return e.contains("/") || e.contains(":") || IPV4_LITERAL.matcher(e).matches();
    }

    static boolean matchesHostEntry(String host, String entry) {
        String e = entry.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty() || isAddressEntry(e)) {
            return false;
        }
        if (e.equals("*")) {
            return true;
        }
        if (e.startsWith("*.")) {
            return host.endsWith(e.substring(1)) && host.length() > e.length() - 1;
        }
        return host.equals(normalize(e));
    }

    /**
     * An address range from the allow-list. A plain address is a range of one.
     */
    record Cidr(byte[] network, int prefix) {

        static Cidr parse(String entry) {
            String e = entry.trim();
            int slash = e.indexOf('/');
            String literal = slash < 0 ? e : e.substring(0, slash);
            byte[] bytes = literalBytes(literal);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Invalid address in apicurio.mcp-import.allowed-hosts: " + e);
            }
            int prefix = slash < 0 ? bytes.length * 8 : Integer.parseInt(e.substring(slash + 1));
            if (prefix < 0 || prefix > bytes.length * 8) {
                throw new IllegalArgumentException("Invalid prefix in apicurio.mcp-import.allowed-hosts: " + e);
            }
            return new Cidr(bytes, prefix);
        }

        boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int full = prefix / 8;
            if (!Arrays.equals(candidate, 0, full, network, 0, full)) {
                return false;
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xff << (8 - rest)) & 0xff;
            return (candidate[full] & mask) == (network[full] & mask);
        }

        /** Parses an IP literal without any DNS lookup, or returns an empty array. */
        private static byte[] literalBytes(String literal) {
            if (!literal.contains(":") && !IPV4_LITERAL.matcher(literal).matches()) {
                return new byte[0];
            }
            try {
                // A literal in one of these forms is parsed locally; no lookup takes place.
                return InetAddress.getByName(literal).getAddress();
            } catch (UnknownHostException e) {
                return new byte[0];
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cidr other && prefix == other.prefix && Arrays.equals(network, other.network);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(network) + prefix;
        }

        @Override
        public String toString() {
            return Arrays.toString(network) + "/" + prefix;
        }
    }
}
