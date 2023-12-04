package io.apicurio.registry.utils;


public class StringUtil {


    public static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }


    public static String limitStr(String value, int limit) {
        return limitStr(value, limit, false);
    }


    public static String limitStr(String value, int limit, boolean withEllipsis) {
        if (StringUtil.isEmpty(value)) {
            return value;
        }

        if (value.length() > limit) {
            if (withEllipsis) {
                return value.substring(0, limit - 3).concat("...");
            } else {
                return value.substring(0, limit);
            }
        } else {
            return value;
        }
    }
}
