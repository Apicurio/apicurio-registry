package io.apicurio.registry.systemtests.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtils {
    public static Logger getLogger() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callersClassName = stackTrace[2].getClassName();
        return LoggerFactory.getLogger(callersClassName);
    }

    public static void logDelimiter(String separatorChar) {
        getLogger().info(separatorChar.repeat(100));
    }
}