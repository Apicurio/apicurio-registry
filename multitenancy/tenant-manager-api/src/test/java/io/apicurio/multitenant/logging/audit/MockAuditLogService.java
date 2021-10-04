/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.multitenant.logging.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.quarkus.test.Mock;

/**
 * @author Fabian Martinez
 */
@Mock
public class MockAuditLogService extends AuditLogService {

    private static final List<Map<String, String>> auditLogs = new CopyOnWriteArrayList<>();

    /**
     * @see io.apicurio.multitenant.logging.audit.AuditLogService#log(java.lang.String, java.lang.String, java.util.Map)
     */
    @Override
    public void log(String action, String result, Map<String, String> metadata, AuditHttpRequestInfo requestInfo) {
        super.log(action, result, metadata, requestInfo);
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
