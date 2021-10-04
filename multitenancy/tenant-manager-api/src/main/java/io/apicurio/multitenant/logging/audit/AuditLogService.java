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

import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import org.slf4j.Logger;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class AuditLogService {

    @Inject
    Logger log;

    @Inject
    AuditHttpRequestContext context;

    @ActivateRequestContext
    public void log(String action, String result, Map<String, String> metadata, AuditHttpRequestInfo requestInfo) {

        String remoteAddress;
        String forwardedRemoteAddress;
        if (requestInfo != null) {
            remoteAddress = requestInfo.getSourceIp();
            forwardedRemoteAddress = requestInfo.getForwardedFor();
        } else {
            remoteAddress = context.getSourceIp();
            forwardedRemoteAddress = context.getForwardedFor();
        }

        StringBuilder m = new StringBuilder();
        m.append("tenant-manager.audit")
            .append(" ")
            .append("action=\"").append(action).append("\" ")
            .append("result=\"").append(result).append("\" ")
            .append("src_ip=\"").append(remoteAddress).append("\" ");
        if (forwardedRemoteAddress != null) {
            m.append("x_forwarded_for=\"").append(forwardedRemoteAddress).append("\" ");
        }
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            m.append(e.getKey()).append("=\"").append(e.getValue()).append("\" ");
        }
        log.info(m.toString());
        //mark in the context that we already generated an audit entry for this request
        context.setAuditEntryGenerated(true);
    }

}
