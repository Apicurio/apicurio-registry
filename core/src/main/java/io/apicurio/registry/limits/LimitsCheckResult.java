package io.apicurio.registry.limits;

public class LimitsCheckResult {

    private boolean allowed;
    private String message;

    public static LimitsCheckResult disallowed(String message) {
        LimitsCheckResult r = new LimitsCheckResult();
        r.allowed = false;
        r.message = message;
        return r;
    }

    public static LimitsCheckResult ok() {
        LimitsCheckResult r = new LimitsCheckResult();
        r.allowed = true;
        return r;
    }

    /**
     * @return the allowed
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

}
