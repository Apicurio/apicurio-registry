package io.apicurio.registry.cli.common;

import lombok.Getter;

public class CliException extends RuntimeException {

    public static final int OK_RETURN_CODE = 0;
    public static final int APPLICATION_ERROR_RETURN_CODE = 1;
    public static final int VALIDATION_ERROR_RETURN_CODE = 2;
    public static final int SERVER_ERROR_RETURN_CODE = 3;

    @Getter
    private int code;

    @Getter
    private boolean quiet;

    public CliException(String message) {
        this(message, null, APPLICATION_ERROR_RETURN_CODE, false);
    }

    public CliException(String message, int code) {
        this(message, null, code, false);
    }

    public CliException(String message, Throwable cause, int code) {
        this(message, cause, code, false);
    }

    public CliException(String message, Throwable cause, int code, boolean quiet) {
        super(message + (cause != null ? " Caused by: " + cause.getMessage() : ""), cause);
        this.code = code;
        this.quiet = quiet;
    }

    public static void exitQuietOk() {
        throw new CliException("Exiting.", null, OK_RETURN_CODE, true);
    }

    public static void exitQuietError(int code) {
        throw new CliException("Exiting.", null, code, true);
    }

    public static void exitQuietServerError() {
        exitQuietError(SERVER_ERROR_RETURN_CODE);
    }
}
