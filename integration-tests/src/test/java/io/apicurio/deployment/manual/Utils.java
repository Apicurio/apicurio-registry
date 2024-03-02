package io.apicurio.deployment.manual;

import io.apicurio.registry.utils.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static String readResource(String path) {
        log.debug("Loading resource: {}", path);
        return IoUtil.toString(Utils.class.getResourceAsStream(path));
    }


    public static final Predicate<Boolean> SELF = x -> x;
}
