/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.RuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class ArtifactRuleKey extends AbstractMessageKey {

    private String groupId;
    private String artifactId;
    private RuleType ruleType;

    /**
     * Creator method.
     * @param tenantId
     * @param groupId
     * @param artifactId
     * @param ruleType
     */
    public static final ArtifactRuleKey create(String tenantId, String groupId, String artifactId, RuleType ruleType) {
        ArtifactRuleKey key = new ArtifactRuleKey();
        key.setTenantId(tenantId);
        key.setGroupId(groupId);
        key.setArtifactId(artifactId);
        key.setRuleType(ruleType);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactRule;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + "/" + groupId + "/" + artifactId;
    }

    /**
     * @return the groupId
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * @return the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * @param artifactId the artifactId to set
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * @return the ruleType
     */
    public RuleType getRuleType() {
        return ruleType;
    }

    /**
     * @param ruleType the ruleType to set
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ArtifactRuleKey [groupId=" + groupId + ", artifactId=" + artifactId + ", ruleType=" + ruleType
                + "]";
    }

}
