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

package io.apicurio.registry.rules.webhook;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.apicurio.registry.rules.RuleViolation;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@RegisterForReflection
public class WebhookExecuteRuleResponse {

    private Boolean success;

    private Set<RuleViolation> errorCauses;


    public WebhookExecuteRuleResponse() {
        super();
    }

    /**
     * @return the success
     */
    public Boolean isSuccess() {
        return success;
    }

    /**
     * @param success the success to set
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * @return the errorCauses
     */
    public Set<RuleViolation> getErrorCauses() {
        return errorCauses;
    }

    /**
     * @param errorCauses the errorCauses to set
     */
    public void setErrorCauses(Set<RuleViolation> errorCauses) {
        this.errorCauses = errorCauses;
    }

}
