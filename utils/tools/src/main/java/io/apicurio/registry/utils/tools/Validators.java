package io.apicurio.registry.utils.tools;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Validators {

    public static final String RFC3986_VALID_URI = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
    public static final Pattern PATTERN_RFC3986_VALID_URI = Pattern.compile(RFC3986_VALID_URI);

    /**
     *  Validates String based URIs according to RFC3986 specifications
     *
     * Regular expression coming from https://tools.ietf.org/html/rfc3986#page-50
     * ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
     *
     * @param str
     * @return
     */
    public static boolean isValidRFC3986URI(String str){
        if (str == null){
            throw new IllegalArgumentException("URI should not be null");
        }
        Matcher matcher = PATTERN_RFC3986_VALID_URI.matcher(str);
        return matcher.matches();
    }
}
