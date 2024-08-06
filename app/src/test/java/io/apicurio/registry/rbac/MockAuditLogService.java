package io.apicurio.registry.rbac;

import io.apicurio.common.apps.logging.audit.AuditHttpRequestInfo;
import io.apicurio.common.apps.logging.audit.AuditLogService;
import io.quarkus.test.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Mock
public class MockAuditLogService extends AuditLogService {

    private static final List<Map<String, String>> auditLogs = new CopyOnWriteArrayList<>();

    /**
     * @see io.apicurio.registry.logging.audit.AuditLogService#log(java.lang.String, java.lang.String,
     *      java.lang.String, java.util.Map, AuditHttpRequestInfo)
     */
    @Override
    public void log(String invoker, String action, String result, Map<String, String> metadata,
            AuditHttpRequestInfo requestInfo) {
        super.log(invoker, action, result, metadata, requestInfo);
        Map<String, String> audit = new HashMap<>(metadata);
        audit.put("action", action);
        audit.put("result", result);
        auditLogs.add(audit);
    }

    public List<Map<String, String>> getAuditLogs() {
        return auditLogs;
    }

    public void resetAuditLogs() {
        auditLogs.clear();
    }

}