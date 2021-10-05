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

import javax.enterprise.context.RequestScoped;

/**
 * @author Fabian Martinez
 */
@RequestScoped
public class AuditHttpRequestContext implements AuditHttpRequestInfo {

    public static final String X_FORWARDED_FOR_HEADER = "x-forwarded-for";
    public static final String FAILURE = "failure";
    public static final String SUCCESS = "success";

    private String sourceIp;
    private String forwardedFor;
    private boolean auditEntryGenerated = false;

    @Override
    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    @Override
    public String getForwardedFor() {
        return forwardedFor;
    }

    public void setForwardedFor(String forwardedFor) {
        this.forwardedFor = forwardedFor;
    }

    public boolean isAuditEntryGenerated() {
        return auditEntryGenerated;
    }

    public void setAuditEntryGenerated(boolean auditEntryGenerated) {
        this.auditEntryGenerated = auditEntryGenerated;
    }

}
